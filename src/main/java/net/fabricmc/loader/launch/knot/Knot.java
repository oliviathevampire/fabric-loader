/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.launch.knot;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.FabricLoader;
import net.fabricmc.loader.game.GameProvider;
import net.fabricmc.loader.game.GameProviders;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.loader.launch.common.FabricMixinBootstrap;
import net.fabricmc.loader.util.UrlConversionException;
import net.fabricmc.loader.util.UrlUtil;
import org.spongepowered.asm.launch.MixinBootstrap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class Knot extends FabricLauncherBase {
	protected Map<String, Object> properties = new HashMap<>();

	private KnotClassLoaderInterface loader;
	private boolean isDevelopment;
	private EnvType envType;
	private final File gameJarFile;
	private GameProvider provider;

	protected Knot(EnvType type, File gameJarFile) {
		this.envType = type;
		this.gameJarFile = gameJarFile;
	}

	protected void init(String[] args) {
		setProperties(properties);

		// configure fabric vars
		if (envType == null) {
			String side = System.getProperty("fabric.side");
			if (side == null) {
				throw new RuntimeException("Please specify side or use a dedicated Knot!");
			}

			switch (side.toLowerCase(Locale.ROOT)) {
				case "client":
					envType = EnvType.CLIENT;
					break;
				case "server":
					envType = EnvType.SERVER;
					break;
				default:
					throw new RuntimeException("Invalid side provided: must be \"client\" or \"server\"!");
			}
		}

		// TODO: Restore these undocumented features
		// String proposedEntrypoint = System.getProperty("fabric.loader.entrypoint");

		List<GameProvider> providers = GameProviders.create();
		provider = null;

		for (GameProvider p : providers) {
			if (p.locateGame(envType, this.getClass().getClassLoader())) {
				provider = p;
				break;
			}
		}

		if (provider != null) {
			LOGGER.info("Loading for game " + provider.getGameName() + " " + provider.getRawGameVersion());
		} else {
			LOGGER.error("Could not find valid game provider!");
			for (GameProvider p : providers) {
				LOGGER.error("- " + p.getGameName()+ " " + p.getRawGameVersion());
			}
			throw new RuntimeException("Could not find valid game provider!");
		}

		provider.acceptArguments(args);

		isDevelopment = Boolean.parseBoolean(System.getProperty("fabric.development", "false"));

		// Setup classloader
		// TODO: Provide KnotCompatibilityClassLoader in non-exclusive-Fabric pre-1.13 environments?
		boolean useCompatibility = provider.requiresUrlClassLoader() || Boolean.parseBoolean(System.getProperty("fabric.loader.useCompatibilityClassLoader", "false"));
		loader = useCompatibility ? new KnotCompatibilityClassLoader(isDevelopment(), envType, provider) : new KnotClassLoader(isDevelopment(), envType, provider);

		if(provider.isObfuscated()) {
			for (Path path : provider.getGameContextJars()) {
				FabricLauncherBase.deobfuscate(
					provider.getGameId(), provider.getNormalizedGameVersion(),
					provider.getLaunchDirectory(),
					path,
					this
				);
			}
		}

		// Locate entrypoints before switching class loaders
		provider.getEntrypointTransformer().locateEntrypoints(this);

		Thread.currentThread().setContextClassLoader((ClassLoader) loader);

		FabricLoader.INSTANCE.setGameProvider(provider);
		FabricLoader.INSTANCE.load();
		FabricLoader.INSTANCE.freeze();

		MixinBootstrap.init();
		FabricMixinBootstrap.init(getEnvironmentType(), FabricLoader.INSTANCE);
		FabricLauncherBase.finishMixinBootstrapping();

		loader.getDelegate().initializeTransformers();

		provider.launch((ClassLoader) loader);
	}

	@Override
	public String getTargetNamespace() {
		// TODO: Won't work outside of Yarn
		return isDevelopment ? "named" : "intermediary";
	}

	@Override
	public Collection<URL> getLoadTimeDependencies() {
		String cmdLineClasspath = System.getProperty("java.class.path");

		return Arrays.stream(cmdLineClasspath.split(File.pathSeparator)).filter((s) -> {
			if (s.equals("*") || s.endsWith(File.separator + "*")) {
				System.err.println("WARNING: Knot does not support wildcard classpath entries: " + s + " - the game may not load properly!");
				return false;
			} else {
				return true;
			}
		}).map((s) -> {
			File file = new File(s);
			if (!file.equals(gameJarFile)) {
				try {
					return (UrlUtil.asUrl(file));
				} catch (UrlConversionException e) {
					LOGGER.debug(e);
					return null;
				}
			} else {
				return null;
			}
		}).filter(Objects::nonNull).collect(Collectors.toSet());
	}

	@Override
	public void propose(URL url) {
		FabricLauncherBase.LOGGER.debug("[Knot] Proposed " + url + " to classpath.");
		loader.addURL(url);
	}

	@Override
	public EnvType getEnvironmentType() {
		return envType;
	}

	@Override
	public boolean isClassLoaded(String name) {
		return loader.isClassLoaded(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		try {
			return loader.getResourceAsStream(name, false);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read file '" + name + "'!", e);
		}
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return (ClassLoader) loader;
	}

	@Override
	public byte[] getClassByteArray(String name) throws IOException {
		return loader.getDelegate().getClassByteArray(name, false);
	}

	@Override
	public boolean isDevelopment() {
		return isDevelopment;
	}

	@Override
	public String getEntrypoint() {
		return provider.getEntrypoint();
	}

	public static void main(String[] args) {
		new Knot(null, null).init(args);
	}
}
