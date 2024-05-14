package felis.aw

import felis.LoaderPluginEntrypoint
import felis.ModLoader
import felis.launcher.OptionScope
import felis.meta.ModMetadataExtended
import net.fabricmc.accesswidener.AccessWidener
import net.fabricmc.accesswidener.AccessWidenerClassVisitor
import net.fabricmc.accesswidener.AccessWidenerReader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object AccessWidenerLoaderPlugin : LoaderPluginEntrypoint, OptionScope {
    private val buildinAccessWideners: List<Path> by option("felis.access.wideners", default(emptyList())) {
        it.split(File.pathSeparator).filter(String::isNotEmpty).map(Paths::get)
    }
    private val logger = LoggerFactory.getLogger(AccessWidener::class.java)
    private val aw = AccessWidener()

    val ModMetadataExtended.accessWidener: String? get() = this["access-widener"]?.toString()

    override fun onLoaderInit() {
        this.logger.info("Initializing Access Widener for 'named' namespace")
        val awReader = AccessWidenerReader(this.aw)

        val configCount = ModLoader.discoverer.mods.mapNotNull { it.metadata.accessWidener }.fold(0) { acc, path ->
            val increasedAmount = ModLoader.classLoader.getResourceAsStream(path)
                ?.use {
                    awReader.read(it.readAllBytes(), "named")
                    1
                }
                ?: 0

            acc + increasedAmount
        }

        for (localAw in this.buildinAccessWideners) {
            Files.newBufferedReader(localAw).use { awReader.read(it, "named") }
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