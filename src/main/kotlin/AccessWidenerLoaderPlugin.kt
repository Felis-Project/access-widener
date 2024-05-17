package felis.aw

import felis.LoaderPluginEntrypoint
import felis.ModLoader
import felis.launcher.OptionScope
import felis.meta.ModMetadataExtended
import net.fabricmc.accesswidener.AccessWidener
import net.fabricmc.accesswidener.AccessWidenerClassVisitor
import net.fabricmc.accesswidener.AccessWidenerFormatException
import net.fabricmc.accesswidener.AccessWidenerReader
import net.fabricmc.accesswidener.TransitiveOnlyFilter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object AccessWidenerLoaderPlugin : LoaderPluginEntrypoint, OptionScope {
    private val localAws: List<Path> by option("felis.access.wideners", default(emptyList())) {
        it.split(File.pathSeparator).filter(String::isNotEmpty).map(Paths::get)
    }
    private val logger = LoggerFactory.getLogger(AccessWidener::class.java)
    private val aw = AccessWidener()

    @Suppress("MemberVisibilityCanBePrivate") // allow mods to access this
    val ModMetadataExtended.accessWidener: String? get() = this["access-widener"]?.toString()

    override fun onLoaderInit() {
        this.logger.info("Initializing Access Widener for 'named' namespace")
        val transitiveReader = AccessWidenerReader(
            if (ModLoader.isAuditing) {
                // only allow transitives in case we are auditing
                TransitiveOnlyFilter(this.aw)
            } else this.aw
        )

        val transitives = ModLoader.discoverer.mods.mapNotNull { it.metadata.accessWidener }.fold(0) { acc, path ->
            val increasedAmount = ModLoader.classLoader.getResourceAsStream(path)?.use {
                transitiveReader.read(it.readAllBytes(), "mojmaps")
                1
            } ?: 0

            acc + increasedAmount
        }

        val localReader = AccessWidenerReader(this.aw)
        val locals = this.localAws.fold(0) { acc, localAw ->
            try {
                Files.newBufferedReader(localAw).use { localReader.read(it, "mojmaps") }
                acc + 1
            } catch (io: IOException) {
                this.logger.info(io.stackTraceToString())
                acc
            } catch (e: AccessWidenerFormatException) {
                this.logger.info(e.stackTraceToString())
                acc
            }
        }

        val configs = locals + transitives

        this.logger.info("Located $configs access widener configuration${if (configs == 1) "" else "s"}. $locals locals and $transitives transitives")

        ModLoader.transformer.registerTransformation {
            if (it.name in this.aw.targets) {
                logger.debug("Transforming ${it.name} with AccessWidener")
                val reader = ClassReader(it.bytes)
                val writer = ClassWriter(0)
                val visitor = AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, writer, this.aw)
                reader.accept(visitor, 0)
                it.newBytes(writer.toByteArray())
            }
        }
    }
}