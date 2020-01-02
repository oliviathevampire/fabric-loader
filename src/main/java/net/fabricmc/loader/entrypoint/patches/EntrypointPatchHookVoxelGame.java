package net.fabricmc.loader.entrypoint.patches;

import com.google.common.base.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.entrypoint.EntrypointPatch;
import net.fabricmc.loader.entrypoint.EntrypointTransformer;
import net.fabricmc.loader.launch.common.FabricLauncher;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.util.ListIterator;
import java.util.function.Consumer;

public class EntrypointPatchHookVoxelGame extends EntrypointPatch {
	public EntrypointPatchHookVoxelGame(EntrypointTransformer transformer) {
		super(transformer);
	}

	private void finishEntrypoint(EnvType type, ListIterator<AbstractInsnNode> it) {
		it.add(new VarInsnNode(Opcodes.ALOAD, 0));
		it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/fabricmc/loader/entrypoint/hooks/Entrypoint" + (type == EnvType.CLIENT ? "Client" : "Server"), "main", "(Ljava/io/File;Ljava/lang/Object;)V", false));
	}

	@Override
	public void process(FabricLauncher launcher, Consumer<ClassNode> classEmitter) {
		EnvType type = launcher.getEnvironmentType();
		String entrypoint = launcher.getEntrypoint();

		if (!entrypoint.startsWith("io.github.vampirestudios.")) {
			return;
		}

		try {
			String clientEntrypoint = null;
			String serverEntrypoint = null;
			// String mainMenuEntrypoint = null;
			boolean serverHasFile = true;
			ClassNode mainClass = loadClass(launcher, entrypoint);

			if (mainClass == null) {
				throw new RuntimeException("Could not load main class " + entrypoint + "!");
			}

			if (type == EnvType.SERVER) {
				throw new RuntimeException("Server environment is unsupported at this time");
			}

			// Each server start will invoke Starter.getServerRunnable where a new server class instance is created.
			if (Strings.isNullOrEmpty(serverEntrypoint)) {
				MethodNode getServerRunnable = findMethod(mainClass, (method) -> method.name.equals("getServerRunnable") && method.desc.equals("(Z)Ljava/lang/Runnable;") && isPublicStatic(method.access));
				if (getServerRunnable == null) {
					throw new RuntimeException("Could not find getServerRunnable method in " + entrypoint + "!");
				}

				// look for invokespecial obfuscated_class_xxx.<init>(Z)V
				MethodInsnNode newServerInsn = (MethodInsnNode) findInsn(getServerRunnable,
					(insn) -> insn.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) insn).desc.equals("(Z)V"),
					true
				);

				if (newServerInsn != null) {
					serverEntrypoint = newServerInsn.owner.replace('/', '.');
				} else {
					throw new RuntimeException("Could not find server constructor in " + entrypoint + "!");
				}

				debug("Found server constructor: " + entrypoint + " -> " + serverEntrypoint);
				ClassNode serverClass = serverEntrypoint.equals(entrypoint) ? mainClass : loadClass(launcher, serverEntrypoint);
				if (serverClass == null) {
					throw new RuntimeException("Could not load server runner " + serverEntrypoint + "!");
				}

				MethodNode gameMethod = serverClass.methods.stream()
					.filter(method -> method.name.equals("run")).findFirst()
					.orElseThrow(() -> new RuntimeException("Could not find server constructor method in " + serverClass.name + "!"));

				debug("Patching server runner " + gameMethod.name + gameMethod.desc);

				ListIterator<AbstractInsnNode> it = gameMethod.instructions.iterator();
				it.add(new InsnNode(Opcodes.ACONST_NULL));
				finishEntrypoint(EnvType.SERVER, it);

				classEmitter.accept(serverClass);
				debug("Patched server runner " + gameMethod.name + gameMethod.desc);
			}

			// Each client start will invoke Starter.startClient where a new client class instance is created. (Actual game play)
			if (Strings.isNullOrEmpty(clientEntrypoint)) {
				MethodNode startClient = findMethod(mainClass, (method) -> method.name.equals("startClient") && method.desc.startsWith("(Lorg/schema/schine/network/client/HostPortLoginName;Z") && isPublicStatic(method.access));
				if (startClient == null) {
					throw new RuntimeException("Could not find startClient method in " + entrypoint + "!");
				}

				// look for invokespecial obfuscated_class_xxx.<init>(Lorg/schema/schine/network/client/HostPortLoginName;ZLobfuscated_class_yyy;)V
				MethodInsnNode newGameInsn = (MethodInsnNode) findInsn(startClient,
					(insn) -> insn.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) insn).desc.startsWith("(Lorg/schema/schine/network/client/HostPortLoginName;Z"),
					true
				);

				if (newGameInsn != null) {
					clientEntrypoint = newGameInsn.owner.replace('/', '.');
				} else {
					throw new RuntimeException("Could not find game constructor in " + entrypoint + "!");
				}

				debug("Found game constructor: " + entrypoint + " -> " + clientEntrypoint);
				ClassNode clientClass = clientEntrypoint.equals(entrypoint) ? mainClass : loadClass(launcher, clientEntrypoint);
				if (clientClass == null) {
					throw new RuntimeException("Could not load client runner " + clientEntrypoint + "!");
				}

				MethodNode gameMethod = clientClass.methods.stream()
					.filter(method -> method.name.equals("run")).findFirst()
					.orElseThrow(() -> new RuntimeException("Could not find game constructor method in " + clientClass.name + "!"));

				debug("Patching client runner " + gameMethod.name + gameMethod.desc);

				ListIterator<AbstractInsnNode> it = gameMethod.instructions.iterator();
				it.add(new InsnNode(Opcodes.ACONST_NULL));
				finishEntrypoint(EnvType.CLIENT, it);

				classEmitter.accept(clientClass);
				debug("Patched client runner " + gameMethod.name + gameMethod.desc);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}