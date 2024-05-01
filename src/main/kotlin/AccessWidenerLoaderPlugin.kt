package felis.aw

import felis.LoaderPluginEntrypoint
import felis.ModLoader
import felis.meta.ModMeta
import felis.transformer.ClassContainer
import felis.transformer.Transformation
import net.fabricmc.accesswidener.AccessWidener
import net.fabricmc.accesswidener.AccessWidenerClassVisitor
import net.fabricmc.accesswidener.AccessWidenerReader
import net.peanuuutz.tomlkt.getStringOrNull
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

object AccessWidenerLoaderPlugin : LoaderPluginEntrypoint {
    private val aw = AccessWidener()

    val ModMeta.accessWidener: String? get() = this.toml.getStringOrNull("access-widener")

    override fun onLoaderInit() {
        val reader = AccessWidenerReader(this.aw)
        for (aw in ModLoader.discoverer
            .mapNotNull { it.meta.accessWidener }
            .map { path -> ModLoader.classLoader.getResourceAsStream(path)?.use { it.readAllBytes() } }
        ) {
            reader.read(aw, "named")
        }
        ModLoader.transformer.registerTransformation(AwTransformation)
    }

    object AwTransformation : Transformation {
        override fun transform(container: ClassContainer) {
            if (container.name in aw.targets) {
                val reader = ClassReader(container.bytes)
                val writer = ClassWriter(0)
                val visitor = AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, writer, aw)
                reader.accept(visitor, 0)
                container.newBytes(writer.toByteArray())
            }
        }
    }
}