package felis.aw

import felis.LoaderPluginEntrypoint
import felis.ModLoader
import felis.launcher.DefaultValue
import felis.launcher.OptionKey
import felis.meta.ModMetadataExtended
import net.fabricmc.accesswidener.AccessWidener
import net.fabricmc.accesswidener.AccessWidenerClassVisitor
import net.fabricmc.accesswidener.AccessWidenerFormatException
import net.fabricmc.accesswidener.AccessWidenerReader
import net.fabricmc.accesswidener.TransitiveOnlyFilter
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object AccessWidenerLoaderPlugin : LoaderPluginEntrypoint {
    private val localAws: List<Path> by OptionKey("felis.access.wideners", DefaultValue.Value(emptyList())) {
        it.split(File.pathSeparator).filter(String::isNotEmpty).map(Paths::get)
    }
    private val logger = LoggerFactory.getLogger(AccessWidener::class.java)

    @Suppress("MemberVisibilityCanBePrivate") // to allow modification to this from the outside
    val aw = AccessWidener()

    @Suppress("MemberVisibilityCanBePrivate") // allow other mods to access this
    val ModMetadataExtended.accessWidener: String? get() = this["access-widener"]?.toString()

    override fun onLoaderInit() {
        this.logger.info("Initializing Access Widener for 'mojmaps' namespace")
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

        this.logger.info("Located $configs access widener configuration${if (configs == 1) "" else "s"} ($locals locals and $transitives transitives)")

        ModLoader.transformer.registerTransformation { container ->
            if (container.name in this.aw.targets) {
                this.logger.debug("Transforming ${container.name} with AccessWidener")
                container.visitor { del -> AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, del, this.aw) }
            } else {
                container
            }
        }
    }
}