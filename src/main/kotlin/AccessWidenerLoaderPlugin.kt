package felis.aw

import felis.LoaderPluginEntrypoint
import felis.ModLoader
import felis.meta.ModMeta
import net.fabricmc.accesswidener.AccessWidener
import net.fabricmc.accesswidener.AccessWidenerClassVisitor
import net.fabricmc.accesswidener.AccessWidenerReader
import net.peanuuutz.tomlkt.getStringOrNull
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory

object AccessWidenerLoaderPlugin : LoaderPluginEntrypoint {
    private val logger = LoggerFactory.getLogger(AccessWidener::class.java)
    private val aw = AccessWidener()

    val ModMeta.accessWidener: String? get() = this.toml.getStringOrNull("access-widener")

    override fun onLoaderInit() {
        this.logger.info("Initializing Access Widener for 'named' namespace")
        val awReader = AccessWidenerReader(this.aw)

        val configCount = ModLoader.discoverer.mapNotNull { it.meta.accessWidener }.fold(0) { acc, path ->
            val increasedAmount = ModLoader.classLoader.getResourceAsStream(path)
                ?.use { it.readAllBytes() }
                ?.let {
                    awReader.read(it, "named")
                    1
                }
                ?: 0

            acc + increasedAmount
        }

        this.logger.info("Located $configCount access widener configuration${if (configCount == 1) "" else "s"}.")
        ModLoader.transformer.registerTransformation {
            if (it.name in aw.targets) {
                logger.debug("Transforming ${it.name} with AccessWidener")
                val reader = ClassReader(it.bytes)
                val writer = ClassWriter(0)
                val visitor = AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, writer, aw)
                reader.accept(visitor, 0)
                it.newBytes(writer.toByteArray())
            }
        }
    }
}