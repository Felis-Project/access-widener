package testmod;
import felis.LoaderPluginEntrypoint;
import felis.ModLoader;

public class Testmod implements LoaderPluginEntrypoint {
    @Override
    public void onLoaderInit() {
        System.out.println(ModLoader.INSTANCE.getGame().getMainClass());
        System.out.println(ModLoader.INSTANCE.getSide());
    }
}
