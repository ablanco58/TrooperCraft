package com.tunombre.stormcraft.client;

import com.tunombre.stormcraft.entity.StarDestroyerEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.entity.Entity;

public final class StarDestroyerSounds {
	private static final Map<UUID, StarDestroyerEngineSound> SOUNDS = new HashMap<>();

	private StarDestroyerSounds() { }

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			var manager = client.getSoundManager();
			if (client.world == null) {
				SOUNDS.values().forEach(manager::stop);
				SOUNDS.clear();
				return;
			}

			Set<UUID> present = new HashSet<>();
			for (Entity entity : client.world.getEntities()) {
				if (!(entity instanceof StarDestroyerEntity ship) || ship.isRemoved()) continue;
				present.add(ship.getUuid());
				StarDestroyerEngineSound sound = SOUNDS.get(ship.getUuid());
				if (sound == null || sound.isAlarm() != ship.isSirenActive() || sound.isDone() || client.world.getTime() % 20 == 0 && !manager.isPlaying(sound)) {
					if (sound != null) manager.stop(sound);
					sound = new StarDestroyerEngineSound(ship);
					SOUNDS.put(ship.getUuid(), sound);
					manager.play(sound);
				}
			}
			SOUNDS.entrySet().removeIf(entry -> {
				if (present.contains(entry.getKey())) return false;
				manager.stop(entry.getValue());
				return true;
			});
		});
	}
}
