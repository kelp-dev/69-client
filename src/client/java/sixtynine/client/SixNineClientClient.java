package sixtynine.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SixNineClientClient implements ClientModInitializer {
	public static final List<Module> MODULES = new ArrayList<>();
	public static final Map<Category, int[]> PANEL_POS = new EnumMap<>(Category.class);
	public static int[][] HUD_POS = {{6, 6}, {6, 52}, {220, 220}, {420, 6}};
	private static KeyMapping menuKey, zoomKey;
	private static int defaultFov = 70;
	private static boolean wasZooming = false;
	private static String lastNotif = "";
	private static long notifTime = 0;

	public enum Category {
		FINDING("Finding"), VISUALS("Visuals"), COMBAT("Combat"),
		MOVEMENT("Movement"), UTILITY("Utility"), HUD("HUD"), CLIENT("Client");
		public final String name;
		Category(String name) { this.name = name; }
	}

	public static class Module {
		public final String name;
		public final Category category;
		public boolean enabled;
		public Module(String name, Category category, boolean def) {
			this.name = name; this.category = category; this.enabled = def;
		}
		public void toggle() {
			this.enabled = !this.enabled;
			onToggle();
			lastNotif = name + ": " + (enabled ? "ON" : "OFF");
			notifTime = System.currentTimeMillis();
		}
		public void onToggle() {}
	}

	private static void add(Category cat, String... names) {
		for (String n : names) MODULES.add(new Module(n, cat, false));
	}

	private static boolean isOn(String name) {
		for (Module m : MODULES) if (m.name.equalsIgnoreCase(name)) return m.enabled;
		return false;
	}

	@Override
	public void onInitializeClient() {
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++) PANEL_POS.put(cats[i], new int[]{10 + i * 82, 12});

		add(Category.FINDING, "Sus Chunk Finder", "Chunk Finder V2", "Chunk Finder ESP", "Hole ESP", "Item ESP", "Light Debug", "Block Entity Debug", "Storage ESP", "Cave Analyzer", "Prime Chunk Finder", "Light Finder");
		add(Category.VISUALS, "Motion Blur", "Hit Particles");
		MODULES.add(new Module("Fullbright", Category.VISUALS, false) {
			@Override public void onToggle() { Minecraft mc = Minecraft.getInstance(); if (mc.options != null) mc.options.gamma().set(enabled ? 100.0 : 1.0); }
		});
		add(Category.VISUALS, "Custom Glint");
		MODULES.add(new Module("Custom FOV", Category.VISUALS, false) {
			@Override public void onToggle() { Minecraft mc = Minecraft.getInstance(); if (mc.options != null) mc.options.fov().set(enabled ? 110 : 70); }
		});
		MODULES.add(new Module("Zoom", Category.VISUALS, true));
		add(Category.VISUALS, "Name Tags", "Weather Notifier", "Hit Box", "Custom Accessories");
		add(Category.COMBAT, "Auto Crystal", "Auto Totem", "Anchor Macro", "Crystal Macro", "Auto Clicker", "KillAura", "Elytra Swap", "Aim Assist", "Hover Totem");
		add(Category.MOVEMENT, "Auto Walk", "Free Look", "Freecam", "Fast Use", "Swing Speed", "Player Utility");
		add(Category.UTILITY, "Fake Stats", "Fake Roles", "Fake Pay", "Name Protect", "Skin Protect", "Coord Snapper", "Spawner Protect", "Spawner Notifier", "Auto TPA", "Chat Macro", "Gamble Rigger", "Spotify Player");

		MODULES.add(new Module("HUD Manager", Category.HUD, true));
		MODULES.add(new Module("Array List", Category.HUD, true));
		MODULES.add(new Module("Armor HUD", Category.HUD, true));
		MODULES.add(new Module("Keystrokes", Category.HUD, true));
		MODULES.add(new Module("Potions", Category.HUD, false));
		MODULES.add(new Module("Radar", Category.HUD, false));
		MODULES.add(new Module("Info", Category.HUD, true));
		MODULES.add(new Module("Watermark", Category.HUD, true));
		MODULES.add(new Module("Staff List", Category.HUD, false));
		MODULES.add(new Module("FPS HUD", Category.HUD, true));
		MODULES.add(new Module("Ping HUD", Category.HUD, true));
		MODULES.add(new Module("Region Map", Category.HUD, false));

		MODULES.add(new Module("Click GUI", Category.CLIENT, false) {
			@Override public void onToggle() { enabled = false; Minecraft.getInstance().setScreen(new ClickGuiScreen()); }
		});
		add(Category.CLIENT, "Themes", "Config Manager", "Icon Picker", "Block List");
		MODULES.add(new Module("Notifications", Category.CLIENT, true));

		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.sixtynine.clickgui", GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));
		zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.sixtynine.zoom", GLFW.GLFW_KEY_C, KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (menuKey.consumeClick()) mc.setScreen(new ClickGuiScreen());
			if (mc.options != null && isOn("Zoom")) {
				if (zoomKey.isDown()) {
					if (!wasZooming) { defaultFov = mc.options.fov().get(); wasZooming = true; }
					mc.options.fov().set(30);
				} else if (wasZooming) { mc.options.fov().set(defaultFov); wasZooming = false; }
			}
		});

		HudRenderCallback.EVENT.register((GuiGraphics g, net.minecraft.client.DeltaTracker delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.options.hideGui) return;
			renderAllHud(g, mc, false);
		});
	}
