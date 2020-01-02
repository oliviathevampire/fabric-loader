package net.fabricmc.loader.game;

import com.google.common.collect.Lists;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.entrypoint.patches.EntrypointPatchHookVoxelGame;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.metadata.BuiltinModMetadata;
import net.fabricmc.loader.util.Arguments;
import net.fabricmc.loader.util.FileSystemUtil;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class VGGameProvider implements GameProvider {

    static class VersionData {
        String id;
		String raw;
		String normalized;
    }

    private EnvType envType;
    private String entrypoint;
	private Path gameJar;
    private VersionData versionData;
    private Arguments arguments;

	private static final EntrypointTransformer TRANSFORMER = new EntrypointTransformer(it -> Collections.singletonList(
		new EntrypointPatchHookVoxelGame(it)
	));

    @Override
    public String getGameId() {
        if (versionData != null) {
            String id = versionData.id;

            if (id != null) {
                return "voxelgame-" + id.replaceAll("[^a-zA-Z0-9.]+", "-");
            }
        }

        return "voxelgame-unknown";
    }

    @Override
    public String getGameName() {
        if (versionData != null && versionData.id != null) {
            return "Voxel Game " + versionData.id;
        }

        return "Voxel Game";
    }

    @Override
    public String getRawGameVersion() {
        return versionData.raw;
    }

    @Override
    public String getNormalizedGameVersion() {
        return versionData.normalized;
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
		URL url;

		try {
			url = gameJar.toUri().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}

		return Collections.singletonList(
			new BuiltinMod(url, new BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
				.setName(getGameName())
				.build())
		);
    }

    @Override
    public String getEntrypoint() {
        return entrypoint;
    }

    @Override
    public Path getLaunchDirectory() {
		if (arguments == null) {
			return new File(".").toPath();
		}

		return FabricLauncherBase.getLaunchDirectory(arguments).toPath();
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
        List<Path> list = new ArrayList<>();
        list.add(gameJar);
        return list;
    }

    @Override
    public boolean locateGame(EnvType envType, ClassLoader loader) {
        this.envType = envType;
        List<String> entrypointClasses = Lists.newArrayList("io.github.vampirestudios.voxel_game.HelloWorld");

        Optional<GameProviderHelper.EntrypointResult> entrypointResult = GameProviderHelper.findFirstClass(loader, entrypointClasses);
        if (!entrypointResult.isPresent()) {
            return false;
        }

        entrypoint = entrypointResult.get().entrypointName;
        gameJar = entrypointResult.get().entrypointPath;

        try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(gameJar, false)) {
            Path versionJson = jarFs.get().getPath("version.json");
            if (Files.exists(versionJson)) {
                versionData = new VersionData();
                String[] versionString = new String(Files.readAllBytes(versionJson), StandardCharsets.UTF_8).split("#");
                versionData.id = versionString[0];
                versionData.raw = versionString[1];
				versionData.normalized = versionString[2];
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public void acceptArguments(String... argStrs) {
		arguments = new Arguments();
		arguments.parse(argStrs);

		FabricLauncherBase.processArgumentMap(arguments, envType);
    }

    @Override
    public EntrypointTransformer getEntrypointTransformer() {
        return TRANSFORMER;
    }

    @Override
    public void launch(ClassLoader loader) {

    }

}