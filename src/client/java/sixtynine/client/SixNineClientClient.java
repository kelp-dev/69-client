package sixtynine.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class SixNineClientClient implements ClientModInitializer {
	public static final List<Module> MODULES = new ArrayList<>();
	private static KeyMapping menuKey;
	private static KeyMapping zoomKey;
	private static int defaultFov = 70;
	private static boolean wasZooming = false;
	private static String lastNotification = "";
	private static long notificationTime = 0;

	public enum Category {
		FINDING("Finding"),
		VISUALS("Visuals"),
		COMBAT("Combat"),
		MOVEMENT("Movement"),
		UTILITY("Utility"),
		HUD("HUD"),
		CLIENT("Client");

		public final String name;
		Category(String name) { this.name = name; }
	}

	public static class Module {
		public final String name;
		public final Category category;
		public boolean enabled;

		public Module(String name, Category category, boolean defaultEnabled) {
			this.name = name;
			this.category = category;
			this.enabled = defaultEnabled;
		}

		public void toggle() {
			this.enabled = !this.enabled;
			onToggle();
			lastNotification = this.name + ": " + (this.enabled ? "ON" : "OFF");
			notificationTime = System.currentTimeMillis();
		}

		public void onToggle() {}
	}

	private static void add(Category cat, String... names) {
		for (String n : names) {
			MODULES.add(new Module(n, cat, false));
		}
	}

	private static Module getMod(String name) {
		for (Module m : MODULES) {
			if (m.name.equalsIgnoreCase(name)) return m;
		}
		return null;
	}

	private static boolean isOn(String name) {
		Module m = getMod(name);
		return m != null && m.enabled;
	}

	@Override
	public void onInitializeClient() {
		// 1. Finding (UI / Visual toggles)
		add(Category.FINDING,
				"Sus Chunk Finder", "Chunk Finder V2", "Chunk Finder ESP",
				"Hole ESP", "Item ESP", "Light Debug", "Block Entity Debug",
				"Storage ESP", "Cave Analyzer", "Prime Chunk Finder", "Light Finder"
		);

		// 2. Visuals (Cu functii legale active pentru Fullbright, Custom FOV si Zoom)
		add(Category.VISUALS, "Motion Blur", "Hit Particles");
		MODULES.add(new Module("Fullbright", Category.VISUALS, false) {
			@Override
			public void onToggle() {
				Minecraft mc = Minecraft.getInstance();
				if (mc.options != null) mc.options.gamma().set(enabled ? 100.0 : 1.0);
			}
		});
		add(Category.VISUALS, "Custom Glint");
		MODULES.add(new Module("Custom FOV", Category.VISUALS, false) {
			@Override
			public void onToggle() {
				Minecraft mc = Minecraft.getInstance();
				if (mc.options != null) mc.options.fov().set(enabled ? 110 : 70);
			}
		});
		MODULES.add(new Module("Zoom", Category.VISUALS, true));
		add(Category.VISUALS, "Name Tags", "Weather Notifier", "Hit Box", "Custom Accessories");

		// 3. Combat (UI toggles)
		add(Category.COMBAT,
				"Auto Crystal", "Auto Totem", "Anchor Macro", "Crystal Macro",
				"Auto Clicker", "KillAura", "Elytra Swap", "Aim Assist", "Hover Totem"
		);

		// 4. Movement (UI toggles)
		add(Category.MOVEMENT,
				"Auto Walk", "Free Look", "Freecam", "Fast Use", "Swing Speed", "Player Utility"
		);

		// 5. Utility (UI toggles)
		add(Category.UTILITY,
				"Fake Stats", "Fake Roles", "Fake Pay", "Name Protect", "Skin Protect",
				"Coord Snapper", "Spawner Protect", "Spawner Notifier", "Auto TPA",
				"Chat Macro", "Gamble Rigger", "Spotify Player"
		);

		// 6. HUD (Functionale)
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

		// 7. Client
		MODULES.add(new Module("Click GUI", Category.CLIENT, false) {
			@Override
			public void onToggle() {
				this.enabled = false;
				Minecraft.getInstance().setScreen(new ClickGuiScreen());
			}
		});
		MODULES.add(new Module("Themes", Category.CLIENT, false));
		MODULES.add(new Module("Config Manager", Category.CLIENT, false));
		MODULES.add(new Module("Icon Picker", Category.CLIENT, false));
		MODULES.add(new Module("Block List", Category.CLIENT, false));
		MODULES.add(new Module("Notifications", Category.CLIENT, true));

		// Taste: Right Shift pentru ClickGUI, C pentru Zoom
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.sixtynine.clickgui", GLFW.GLFW_KEY_RIGHT_SHIFT, "category.sixtynine"
		));
		zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.sixtynine.zoom", GLFW.GLFW_KEY_C, "category.sixtynine"
		));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (menuKey.consumeClick()) {
				mc.setScreen(new ClickGuiScreen());
			}
			if (mc.options != null && isOn("Zoom")) {
				if (zoomKey.isDown()) {
					if (!wasZooming) {
						defaultFov = mc.options.fov().get();
						wasZooming = true;
					}
					mc.options.fov().set(30);
				} else if (wasZooming) {
					mc.options.fov().set(defaultFov);
					wasZooming = false;
				}
			}
		});

		// Randare HUD
		HudRenderCallback.EVENT.register((GuiGraphics g, net.minecraft.client.DeltaTracker delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.options.hideGui) return;

			int accent = isOn("Themes") ? 0xFF00E5FF : 0xFF9B59B6;
			int y = 6;
			int screenW = mc.getWindow().getGuiScaledWidth();
			int screenH = mc.getWindow().getGuiScaledHeight();

			if (isOn("Watermark")) {
				g.fill(4, y - 2, 108, y + 10, 0x90000000);
				g.drawString(mc.font, "69 CLIENT v1.0", 8, y, accent, true);
				y += 14;
			}
			if (isOn("FPS HUD")) {
				g.drawString(mc.font, "FPS: " + mc.getFps(), 6, y, 0xFFFFFFFF, true);
				y += 11;
			}
			if (isOn("Ping HUD") && mc.getConnection() != null && mc.getConnection().getPlayerInfo(mc.player.getUUID()) != null) {
				int ping = mc.getConnection().getPlayerInfo(mc.player.getUUID()).getLatency();
				g.drawString(mc.font, "Ping: " + ping + "ms", 6, y, 0xFF55FF55, true);
				y += 11;
			}
			if (isOn("Info")) {
				String coords = String.format("XYZ: %d, %d, %d", (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
				g.drawString(mc.font, coords, 6, y, 0xFFDDDDDD, true);
				y += 12;
			}

			if (isOn("Keystrokes")) {
				int kx = 28, ky = y + 4;
				drawKey(g, mc, "W", kx, ky, mc.options.keyUp.isDown(), accent);
				drawKey(g, mc, "A", kx - 22, ky + 22, mc.options.keyLeft.isDown(), accent);
				drawKey(g, mc, "S", kx, ky + 22, mc.options.keyDown.isDown(), accent);
				drawKey(g, mc, "D", kx + 22, ky + 22, mc.options.keyRight.isDown(), accent);
			}

			if (isOn("Armor HUD")) {
				int ax = screenW / 2 + 12;
				int ay = screenH - 55;
				EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
				for (EquipmentSlot slot : slots) {
					ItemStack stack = mc.player.getItemBySlot(slot);
					if (!stack.isEmpty()) {
						g.renderItem(stack, ax, ay);
						g.renderItemDecorations(mc.font, stack, ax, ay);
						ax += 18;
					}
				}
			}

			if (isOn("Potions")) {
				int py = screenH / 2;
				for (MobEffectInstance effect : mc.player.getActiveEffects()) {
					String name = effect.getEffect().value().getDisplayName().getString() + " (" + (effect.getDuration() / 20) + "s)";
					g.drawString(mc.font, name, 6, py, accent, true);
					py += 11;
				}
			}

			if (isOn("Array List")) {
				int ay = 6;
				for (Module m : MODULES) {
					if (m.enabled) {
						int w = mc.font.width(m.name);
						g.fill(screenW - w - 8, ay - 2, screenW - 2, ay + 9, 0x80000000);
						g.fill(screenW - 2, ay - 2, screenW, ay + 9, accent);
						g.drawString(mc.font, m.name, screenW - w - 5, ay, 0xFFFFFFFF, true);
						ay += 11;
					}
				}
			}

			if (isOn("Notifications") && System.currentTimeMillis() - notificationTime < 2000 && !lastNotification.isEmpty()) {
				int nw = mc.font.width(lastNotification) + 12;
				g.fill(screenW - nw - 6, screenH - 28, screenW - 6, screenH - 12, 0xCC111111);
				g.fill(screenW - nw - 6, screenH - 28, screenW - nw - 4, screenH - 12, accent);
				g.drawString(mc.font, lastNotification, screenW - nw, screenH - 24, 0xFFFFFFFF, true);
			}
		});
	}

	private static void drawKey(GuiGraphics g, Minecraft mc, String label, int x, int y, boolean pressed, int accent) {
		g.fill(x, y, x + 20, y + 20, pressed ? (accent & 0x00FFFFFF) | 0xAA000000 : 0x80000000);
		g.drawCenteredString(mc.font, label, x + 10, y + 6, 0xFFFFFFFF);
	}

	public static class ClickGuiScreen extends Screen {
		protected ClickGuiScreen() {
			super(Component.literal("69 Client ClickGUI"));
		}

		@Override
		public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
			g.fill(0, 0, this.width, this.height, 0x65000000);
			int accent = isOn("Themes") ? 0xFF00E5FF : 0xFF8E44AD;
			int cols = 4;
			int panelW = 105;
			int gap = 10;
			int startX = 20;
			int startY = 20;

			
				Category[] cats = Category.values();
				for (int i = 0; i < cats.length; i++) {
					Category cat = cats[i];
					int col = i % cols;
					int row = i / cols;
					int px = startX + col * (panelW + gap);
					int py = row == 0 ? startY : startY + 195;

					int my = py + 15;
					for (Module m : MODULES) {
						if (m.category == cat) {
							if (mouseX >= px && mouseX <= px + panelW && mouseY >= my && mouseY <= my + 13) {
								m.toggle();
								return true;
							}
							my += 13;
						}
					}
				}
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}
	}
}

