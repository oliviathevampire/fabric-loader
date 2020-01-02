package net.fabricmc.loader.game;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.entrypoint.minecraft.EntrypointPatchBranding;
import net.fabricmc.loader.entrypoint.minecraft.EntrypointPatchFML125;
import net.fabricmc.loader.entrypoint.minecraft.EntrypointPatchHook;
import net.fabricmc.loader.metadata.BuiltinModMetadata;
import net.fabricmc.loader.util.Arguments;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RockBottomGameProvider implements GameProvider {

    private Arguments arguments;

	public static final EntrypointTransformer TRANSFORMER = new EntrypointTransformer(it -> Arrays.asList(
			new EntrypointPatchHook(it),
			new EntrypointPatchBranding(it),
			new EntrypointPatchFML125(it)
	));

    @Override
    public String getGameId() {
        return "rockbottom";
    }

    @Override
    public String getGameName() {
        return "Rock Bottom";
    }

    @Override
    public String getRawGameVersion() {
        return "0.3.7";
    }

    @Override
    public String getNormalizedGameVersion() {
        return "0.3.7";
    }

    @Override
    public Collection<GameProvider.BuiltinMod> getBuiltinMods() {
        return Collections.singletonList(
                new GameProvider.BuiltinMod(null, new BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
                        .setName(getGameName())
                        .build())
        );
    }

    @Override
    public String getEntrypoint() {
        return "de.ellpeck.rockbottom.Main";
    }

    @Override
    public Path getLaunchDirectory() {
        return new File(".").toPath();
    }

    @Override
    public boolean isObfuscated() {
        return false;
    }

    @Override
    public boolean requiresUrlClassLoader() {
        return false;
    }

    @Override
    public List<Path> getGameContextJars() {
        return null;
    }

    @Override
    public boolean locateGame(EnvType envType, ClassLoader loader) {
        return true;
    }

    @Override
    public void acceptArguments(String... argStrings) {
        this.arguments = new Arguments();
        arguments.parse(argStrings);
    }

	@Override
	public EntrypointTransformer getEntrypointTransformer() {
		return null;
	}

	@Override
    public void launch(ClassLoader loader) {
        try {
            Class<?> c = loader.loadClass("de.ellpeck.rockbottom.Main");
            Method m = c.getMethod("main", String[].class);
            m.invoke(null, (Object) arguments.toArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
