package com.tunombre.stormcraft.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tunombre.stormcraft.TroperCraftMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/** The same manifest drives registration and the duration-aware speech scheduler. */
public final class TrooperVoices {
	public record VoiceLine(String id, SoundEvent sound, int durationTicks, String mood) {
		public boolean matches(boolean combat) {
			return mood.equals("both") || mood.equals(combat ? "combat" : "patrol");
		}
	}

	private static List<VoiceLine> lines = List.of();

	private TrooperVoices() { }

	public static void register() {
		String path = "/assets/" + TroperCraftMod.MOD_ID + "/voice_lines.json";
		try (InputStream input = TroperCraftMod.class.getResourceAsStream(path)) {
			if (input == null) throw new IllegalStateException("Falta el banco de voces: " + path);
			JsonObject manifest = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
			List<VoiceLine> loaded = new ArrayList<>();
			for (JsonElement element : manifest.getAsJsonArray("clips")) {
				JsonObject clip = element.getAsJsonObject();
				String name = clip.get("id").getAsString();
				int duration = clip.get("durationTicks").getAsInt();
				String mood = clip.get("mood").getAsString();
				if (duration < 1 || duration > 1200 || !List.of("both", "patrol", "combat").contains(mood)) {
					throw new IllegalStateException("Clip de voz inválido: " + name);
				}
				Identifier id = TroperCraftMod.id(name);
				SoundEvent sound = Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id, ModSounds.VOICE_RANGE));
				loaded.add(new VoiceLine(name, sound, duration, mood));
			}
			if (loaded.stream().noneMatch(line -> line.matches(false))
					|| loaded.stream().noneMatch(line -> line.matches(true))) {
				throw new IllegalStateException("El banco necesita voces para patrulla y combate");
			}
			lines = List.copyOf(loaded);
			TroperCraftMod.LOGGER.info("Cargados {} clips de voz para los stormtroopers.", lines.size());
		} catch (IOException exception) {
			throw new IllegalStateException("No se pudo leer el banco de voces", exception);
		}
	}

	public static List<VoiceLine> lines() {
		return lines;
	}

	public static VoiceLine choose(boolean combat, String previousId, Random random) {
		List<VoiceLine> candidates = lines.stream().filter(line -> line.matches(combat)).toList();
		List<VoiceLine> different = candidates.stream().filter(line -> !line.id().equals(previousId)).toList();
		List<VoiceLine> choices = different.isEmpty() ? candidates : different;
		return choices.get(random.nextInt(choices.size()));
	}
}
