using System;
using System.Linq;
using System.Text.RegularExpressions;

namespace KotlinArtisan
{
    /// <summary>Same ranking as artisan's own search (core/Fuzzy.kt), without the typo tier. Null = no match.</summary>
    public static class Fuzzy
    {
        public static int? Score(string query, string name, string description)
        {
            var q = query.ToLowerInvariant();
            var n = name.ToLowerInvariant();
            var d = (description ?? "").ToLowerInvariant();
            if (q.Length == 0) return 0;
            var tail = n.Contains(':') ? n.Substring(n.IndexOf(':') + 1) : n;
            var parts = n.Split(new[] { ':', '-', '_' }, StringSplitOptions.RemoveEmptyEntries);
            if (n == q) return 1000;
            if (n.StartsWith(q, StringComparison.Ordinal)) return 900;
            if (tail.StartsWith(q, StringComparison.Ordinal)) return 850;
            if (parts.Any(p => p.StartsWith(q, StringComparison.Ordinal))) return 800;
            if (new string(parts.Select(p => p[0]).ToArray()).StartsWith(q, StringComparison.Ordinal)) return 700;
            var i = n.IndexOf(q, StringComparison.Ordinal);
            if (i >= 0) return 650 - i;
            if (q.Length >= 3 && Regex.IsMatch(d, @"\b" + Regex.Escape(q))) return 500;
            var gaps = Gaps(q, tail);
            if (gaps != null) return 400 - gaps;
            if (q.Length >= 3 && d.Contains(q)) return 300;
            return null;
        }

        /// <summary>Letters of q in order inside s: how many were skipped, or null when they aren't all there.</summary>
        private static int? Gaps(string q, string s)
        {
            int i = 0, skipped = 0;
            foreach (var c in s)
            {
                if (i < q.Length && c == q[i]) i++;
                else if (i > 0 && i < q.Length) skipped++;
            }
            return i == q.Length ? skipped : null;
        }
    }
}
