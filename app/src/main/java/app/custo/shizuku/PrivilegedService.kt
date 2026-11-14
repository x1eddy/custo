package app.custo.shizuku

import app.custo.IPrivilegedService
import kotlin.system.exitProcess

/**
 * Runs inside a separate process started by Shizuku with shell (ADB) privileges.
 * Must keep a public no-arg constructor: Shizuku instantiates it by reflection.
 */
class PrivilegedService : IPrivilegedService.Stub() {

    private companion object {
        const val SHELL = "com.android.shell"
    }

    override fun destroy() {
        exitProcess(0)
    }

    // ---- Fabricated overlays via hidden OverlayManager APIs (reflection; runs as shell uid) ----

    private val overlayManager: Any by lazy {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "overlay")
        Class.forName("android.content.om.IOverlayManager\$Stub").getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)!!
    }

    private fun currentUser(): Int = try {
        Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null) as Int
    } catch (_: Throwable) { 0 }

    private fun commit(build: (Any) -> Unit): String? = try {
        val builderCls = Class.forName("android.content.om.OverlayManagerTransaction\$Builder")
        val builder = builderCls.getConstructor().newInstance()
        build(builder)
        val tx = builderCls.getMethod("build").invoke(builder)
        overlayManager.javaClass.getMethod("commit", Class.forName("android.content.om.OverlayManagerTransaction")).invoke(overlayManager, tx)
        null
    } catch (t: Throwable) {
        val root = generateSequence(t) { it.cause }.last()
        "${root.javaClass.simpleName}: ${root.message}"
    }

    override fun fabricate(target: String, name: String, resource: String, type: Int, value: Int): String? {
        return commit { builder ->
            val foBuilderCls = Class.forName("android.content.om.FabricatedOverlay\$Builder")
            val foBuilder = foBuilderCls.getConstructor(String::class.java, String::class.java, String::class.java)
                .newInstance(SHELL, name, target)
            foBuilderCls.getMethod("setResourceValue", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(foBuilder, resource, type, value)
            val fo = foBuilderCls.getMethod("build").invoke(foBuilder)
            val foCls = Class.forName("android.content.om.FabricatedOverlay")
            builder.javaClass.getMethod("registerFabricatedOverlay", foCls).invoke(builder, fo)
            val id = foCls.getMethod("getIdentifier").invoke(fo)
            val idCls = Class.forName("android.content.om.OverlayIdentifier")
            builder.javaClass.getMethod("setEnabled", idCls, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(builder, id, true, currentUser())
        }
    }

    override fun unfabricate(name: String): String? {
        return commit { builder ->
            val idCls = Class.forName("android.content.om.OverlayIdentifier")
            val id = idCls.getConstructor(String::class.java, String::class.java).newInstance(SHELL, name)
            builder.javaClass.getMethod("unregisterFabricatedOverlay", idCls).invoke(builder, id)
        }
    }

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            "$code\n$output"
        } catch (t: Throwable) {
            "-1\n${t.message}"
        }
    }
}
