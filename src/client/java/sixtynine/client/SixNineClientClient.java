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
		public static void renderAllHud(GuiGraphics g, Minecraft mc, boolean editing) {
		float scale = 0.65f;
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);

		int accent = isOn("Themes") ? 0xFF00E5FF : 0xFF9B59B6;
		int screenW = (int) (mc.getWindow().getGuiScaledWidth() / scale);
		int screenH = (int) (mc.getWindow().getGuiScaledHeight() / scale);

		int ix = (int) (HUD_POS[0][0] / scale);
		int iy = (int) (HUD_POS[0][1] / scale);
		int startIy = iy;
		if (isOn("Watermark")) {
			g.fill(ix - 2, iy - 2, ix + 86, iy + 9, 0x90000000);
			g.drawString(mc.font, "69 CLIENT", ix, iy, accent, true);
			iy += 11;
		}
		if (isOn("FPS HUD")) { g.drawString(mc.font, "FPS: " + mc.getFps(), ix, iy, 0xFFFFFFFF, true); iy += 10; }
		if (isOn("Ping HUD") && mc.getConnection() != null && mc.getConnection().getPlayerInfo(mc.player.getUUID()) != null) {
			g.drawString(mc.font, "Ping: " + mc.getConnection().getPlayerInfo(mc.player.getUUID()).getLatency() + "ms", ix, iy, 0xFF55FF55, true);
			iy += 10;
		}
		if (isOn("Info")) {
			g.drawString(mc.font, String.format("XYZ: %d %d %d", (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ()), ix, iy, 0xFFDDDDDD, true);
			iy += 10;
		}
		if (editing) g.renderOutline(ix - 3, startIy - 3, 92, Math.max(14, iy - startIy + 4), accent);

		if (isOn("Keystrokes")) {
			int kx = (int) (HUD_POS[1][0] / scale) + 18;
			int ky = (int) (HUD_POS[1][1] / scale);
			drawKey(g, mc, "W", kx, ky, mc.options.keyUp.isDown(), accent);
			drawKey(g, mc, "A", kx - 18, ky + 18, mc.options.keyLeft.isDown(), accent);
			drawKey(g, mc, "S", kx, ky + 18, mc.options.keyDown.isDown(), accent);
			drawKey(g, mc, "D", kx + 18, ky + 18, mc.options.keyRight.isDown(), accent);
			if (editing) g.renderOutline(kx - 20, ky - 2, 56, 38, accent);
		}

		if (isOn("Armor HUD")) {
			int ax = (int) (HUD_POS[2][0] / scale);
			int ay = (int) (HUD_POS[2][1] / scale);
			int startAx = ax;
			for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ItemStack stack = mc.player.getItemBySlot(slot);
				if (!stack.isEmpty()) { g.renderItem(stack, ax, ay); g.renderItemDecorations(mc.font, stack, ax, ay); ax += 17; }
			}
			if (editing) g.renderOutline(startAx - 2, ay - 2, 70, 20, accent);
		}

		if (isOn("Potions")) {
			int py = screenH / 2;
			for (MobEffectInstance eff : mc.player.getActiveEffects()) {
				g.drawString(mc.font, eff.getEffect().value().getDisplayName().getString() + " " + (eff.getDuration() / 20) + "s", 6, py, accent, true);
				py += 10;
			}
		}

		if (isOn("Array List")) {
			int rx = (int) (HUD_POS[3][0] / scale);
			int ay = (int) (HUD_POS[3][1] / scale);
			int startAy = ay;
			for (Module m : MODULES) {
				if (m.enabled) {
					int w = mc.font.width(m.name);
					g.fill(rx - w - 6, ay - 1, rx, ay + 9, 0x80000000);
					g.fill(rx, ay - 1, rx + 2, ay + 9, accent);
					g.drawString(mc.font, m.name, rx - w - 3, ay, 0xFFFFFFFF, true);
					ay += 10;
				}
			}
			if (editing) g.renderOutline(rx - 80, startAy - 2, 84, Math.max(14, ay - startAy + 4), accent);
		}

		if (isOn("Notifications") && System.currentTimeMillis() - notifTime < 2000 && !lastNotif.isEmpty()) {
			int nw = mc.font.width(lastNotif) + 10;
			g.fill(screenW - nw - 6, screenH - 24, screenW - 6, screenH - 10, 0xCC111111);
			g.drawString(mc.font, lastNotif, screenW - nw - 1, screenH - 21, 0xFFFFFFFF, true);
		}
		g.pose().popMatrix();
	}

		private static void drawKey(GuiGraphics g, Minecraft mc, String label, int x, int y, boolean pressed, int accent) {
		g.fill(x, y, x + 16, y + 16, pressed ? (accent & 0x00FFFFFF) | 0xAA000000 : 0x80000000);
		g.drawCenteredString(mc.font, label, x + 8, y + 4, 0xFFFFFFFF);
	}

	public static class ClickGuiScreen extends Screen {
		private Category dragCat = null;
		private int dragHud = -1;
		private int offX = 0, offY = 0;
		private final int pw = 78, hh = 11, mh = 9;

		protected ClickGuiScreen() { super(Component.literal("69 Client")); }

		@Override
		public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
			g.fill(0, 0, this.width, this.height, 0x55000000);
			if (this.minecraft != null && this.minecraft.player != null) renderAllHud(g, this.minecraft, true);

			int accent = isOn("Themes") ? 0xFF00E5FF : 0xFF8E44AD;
			for (Category cat : Category.values()) {
				int[] pos = PANEL_POS.get(cat);
				int px = pos[0], py = pos[1];
				g.fill(px, py, px + pw, py + hh, accent);
				g.drawCenteredString(this.font, cat.name, px + pw / 2, py + 2, 0xFFFFFFFF);

				int my = py + hh;
				for (Module m : MODULES) {
					if (m.category == cat) {
						boolean hov = mouseX >= px && mouseX <= px + pw && mouseY >= my && mouseY <= my + mh;
						int bg = m.enabled ? ((accent & 0x00FFFFFF) | 0xBB000000) : (hov ? 0xCC2C3E50 : 0xCC161616);
						g.fill(px, my, px + pw, my + mh, bg);
						g.drawString(this.font, m.name, px + 2, my + 1, m.enabled ? 0xFFFFFFFF : 0xFFBBBBBB, true);
						my += mh;
					}
				}
			}
			super.render(g, mouseX, mouseY, delta);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
			if (event.button() == 0) {
				int mx = (int) event.x(), my = (int) event.y();
				for (Category cat : Category.values()) {
					int[] pos = PANEL_POS.get(cat);
					int px = pos[0], py = pos[1];
					if (mx >= px && mx <= px + pw && my >= py && my <= py + hh) {
						dragCat = cat; offX = mx - px; offY = my - py; return true;
					}
					int curY = py + hh;
					for (Module m : MODULES) {
						if (m.category == cat) {
							if (mx >= px && mx <= px + pw && my >= curY && my <= curY + mh) { m.toggle(); return true; }
							curY += mh;
						}
					}
				}
				for (int i = 0; i < HUD_POS.length; i++) {
					int hx = HUD_POS[i][0], hy = HUD_POS[i][1];
					int hitX = (i == 3) ? hx - 55 : hx;
					if (mx >= hitX && mx <= hitX + 65 && my >= hy && my <= hy + 35) {
						dragHud = i; offX = mx - hx; offY = my - hy; return true;
					}
				}
			}
			return super.mouseClicked(event, doubled);
		}

		@Override
		public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
			if (event.button() == 0) {
				if (dragCat != null) {
					int[] pos = PANEL_POS.get(dragCat);
					pos[0] = (int) event.x() - offX; pos[1] = (int) event.y() - offY; return true;
				}
				if (dragHud != -1) {
					HUD_POS[dragHud][0] = (int) event.x() - offX; HUD_POS[dragHud][1] = (int) event.y() - offY; return true;
				}
			}
			return super.mouseDragged(event, dx, dy);
		}

		@Override
		public boolean mouseReleased(MouseButtonEvent event) {
			if (event.button() == 0) { dragCat = null; dragHud = -1; }
			return super.mouseReleased(event);
		}
	}
}
