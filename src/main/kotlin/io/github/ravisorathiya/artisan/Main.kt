package io.github.ravisorathiya.artisan

import io.github.ravisorathiya.artisan.commands.registerApp
import io.github.ravisorathiya.artisan.commands.registerCode
import io.github.ravisorathiya.artisan.commands.registerCustom
import io.github.ravisorathiya.artisan.commands.registerMisc
import io.github.ravisorathiya.artisan.commands.registerNotes
import io.github.ravisorathiya.artisan.commands.registerOpen
import io.github.ravisorathiya.artisan.core.Cancelled
import io.github.ravisorathiya.artisan.core.CliError
import io.github.ravisorathiya.artisan.core.Ctx
import io.github.ravisorathiya.artisan.core.Registry
import io.github.ravisorathiya.artisan.core.Tone
import io.github.ravisorathiya.artisan.core.Ui
import kotlin.system.exitProcess

val VERSION: String = Ctx::class.java.`package`?.implementationVersion ?: "dev"

fun main(argv: Array<String>) {
    val ctx = Ctx(argv.drop(1))
    Ui.assumeYes = ctx.yes
    val code = try {
        registerMisc()
        registerNotes()
        registerCode()
        registerApp()
        registerOpen()
        ctx.projectOrNull?.let { registerCustom(it) }
        Registry.dispatch(argv.firstOrNull() ?: "list", ctx)
        0
    } catch (e: Cancelled) {
        Ui.say("Cancelled.", Tone.WARN)
        0
    } catch (e: CliError) {
        Ui.say("error: ${e.message}", Tone.BAD)
        1
    } catch (e: Exception) {
        Ui.say("error: ${e.message ?: e.javaClass.simpleName}", Tone.BAD)
        if (System.getenv("ARTISAN_DEBUG") != null) e.printStackTrace() else Ui.say("Set ARTISAN_DEBUG=1 for the stack trace.", Tone.DIM)
        1
    }
    exitProcess(code)
}
