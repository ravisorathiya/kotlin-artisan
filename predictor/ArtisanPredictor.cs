using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Management.Automation;
using System.Management.Automation.Subsystem;
using System.Management.Automation.Subsystem.Prediction;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;

namespace KotlinArtisan
{
    /// <summary>A command, value or flag from artisan's commands.json.</summary>
    public sealed class Item
    {
        public string Name { get; set; } = "";
        public string Usage { get; set; } = "";
        public string Description { get; set; } = "";
        public List<Item> Choices { get; set; } = new List<Item>();

        /// <summary>The usage line has [arguments] after the name.</summary>
        public bool TakesValue => Usage.Length > Name.Length;
    }

    /// <summary>
    /// Suggests artisan commands while typing `artisan ...` in PowerShell 7.2+. The command list comes from
    /// commands.json, which artisan writes next to its completion script (path in ARTISAN_COMPLETION_DATA).
    /// </summary>
    public sealed class ArtisanPredictor : ICommandPredictor
    {
        public static readonly Guid PredictorId = new Guid("6f1c2a43-8a3e-4c6b-9a57-2f0d5b7e3c11");
        public static readonly ArtisanPredictor Instance = new ArtisanPredictor();

        private static readonly Regex Prefix = new Regex(@"^\s*(?:\.[\\/])?(?:kotlin-)?artisan(?:\.bat|\.cmd)?\s+", RegexOptions.IgnoreCase);
        private const int Max = 5;

        private readonly object gate = new object();
        private List<Item> commands = new List<Item>();
        private List<Item> flags = new List<Item>();
        private DateTime loadedStamp = DateTime.MinValue;
        private DateTime lastCheck = DateTime.MinValue;

        public Guid Id => PredictorId;
        public string Name => "artisan";
        public string Description => "kotlin-artisan commands, values and flags as you type";

        public SuggestionPackage GetSuggestion(PredictionClient client, PredictionContext context, CancellationToken cancellationToken)
        {
            var suggestions = Suggest(context.InputAst.Extent.Text);
            return suggestions.Count == 0 ? default : new SuggestionPackage(suggestions);
        }

        /// <summary>Full command lines that complete [line], best first; empty when it isn't an artisan command.</summary>
        public List<PredictiveSuggestion> Suggest(string line)
        {
            var result = new List<PredictiveSuggestion>();
            var m = Prefix.Match(line);
            if (!m.Success) return result;
            Load();
            var rest = line.Substring(m.Length);
            var word = Regex.Match(rest, @"(\S*)$").Groups[1].Value;
            var before = rest.Substring(0, rest.Length - word.Length)
                .Split(new[] { ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries).Where(w => !w.StartsWith("-")).ToList();

            List<Item> list;
            if (word.StartsWith("-")) list = flags;
            else if (before.Count == 0) list = commands;
            else if (before.Count == 1) list = commands.FirstOrDefault(c => string.Equals(c.Name, before[0], StringComparison.OrdinalIgnoreCase))?.Choices ?? new List<Item>();
            else return result;

            var head = line.Substring(0, line.Length - word.Length);
            var ranked = list
                .Select((item, index) => (item, index, score: Fuzzy.Score(word, item.Name, item.Description)))
                .Where(x => x.score != null)
                .OrderByDescending(x => x.score).ThenBy(x => x.index)
                .Select(x => x.item)
                .Where(x => !string.Equals(x.Name, word, StringComparison.OrdinalIgnoreCase))
                .Take(Max);
            foreach (var item in ranked)
            {
                var text = head + item.Name + (before.Count == 0 && item.TakesValue ? " " : "");
                var tip = string.IsNullOrEmpty(item.Usage) ? item.Description : item.Usage + "  " + item.Description;
                result.Add(new PredictiveSuggestion(text, tip));
            }
            return result;
        }

        /// <summary>(Re)reads commands.json when it changed; checks the file at most every two seconds.</summary>
        private void Load()
        {
            lock (gate)
            {
                var now = DateTime.UtcNow;
                if ((now - lastCheck).TotalSeconds < 2 && commands.Count > 0) return;
                lastCheck = now;
                var path = Environment.GetEnvironmentVariable("ARTISAN_COMPLETION_DATA");
                if (string.IsNullOrEmpty(path) || !File.Exists(path)) return;
                var stamp = File.GetLastWriteTimeUtc(path);
                if (stamp == loadedStamp) return;
                try
                {
                    using var doc = JsonDocument.Parse(File.ReadAllText(path));
                    commands = Items(doc.RootElement, "commands");
                    flags = Items(doc.RootElement, "flags");
                    loadedStamp = stamp;
                }
                catch (Exception)
                {
                    // half-written file during an update: try again on the next check
                }
            }
        }

        private static List<Item> Items(JsonElement parent, string key)
        {
            var list = new List<Item>();
            if (!parent.TryGetProperty(key, out var array) || array.ValueKind != JsonValueKind.Array) return list;
            foreach (var e in array.EnumerateArray())
            {
                list.Add(new Item
                {
                    Name = Text(e, "name"),
                    Usage = Text(e, "usage"),
                    Description = Text(e, "description"),
                    Choices = Items(e, "choices"),
                });
            }
            return list;
        }

        private static string Text(JsonElement e, string key) =>
            e.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() ?? "" : "";

        // Feedback from PSReadLine isn't used.
        public bool CanAcceptFeedback(PredictionClient client, PredictorFeedbackKind feedback) => false;
        public void OnSuggestionDisplayed(PredictionClient client, uint session, int countOrIndex) { }
        public void OnSuggestionAccepted(PredictionClient client, uint session, string acceptedSuggestion) { }
        public void OnCommandLineAccepted(PredictionClient client, IReadOnlyList<string> history) { }
        public void OnCommandLineExecuted(PredictionClient client, string commandLine, bool success) { }
    }

    /// <summary>Registers the predictor when the DLL is imported (Import-Module) and removes it again on Remove-Module.</summary>
    public sealed class Init : IModuleAssemblyInitializer, IModuleAssemblyCleanup
    {
        public void OnImport()
        {
            try
            {
                SubsystemManager.RegisterSubsystem(SubsystemKind.CommandPredictor, ArtisanPredictor.Instance);
            }
            catch (InvalidOperationException)
            {
                // already registered (the profile ran twice)
            }
        }

        public void OnRemove(PSModuleInfo psModuleInfo) =>
            SubsystemManager.UnregisterSubsystem(SubsystemKind.CommandPredictor, ArtisanPredictor.PredictorId);
    }
}
