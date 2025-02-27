/*
 * Modern UI.
 * Copyright (C) 2019-2023 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc.text;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import icyllis.modernui.ModernUI;
import icyllis.modernui.graphics.text.Font;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class MuiTextCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(ModernUI.ID)
                .then(Commands.literal("text")
                        .then(Commands.literal("layout")
                                .then(Commands.argument("message", ComponentArgument.textComponent())
                                        .executes(ctx -> {
                                            layout(
                                                    ctx.getSource(),
                                                    ComponentArgument.getComponent(ctx, "message")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
        );
    }

    private static void layout(CommandSourceStack source,
                               Component component) {
        TextLayout layout = TextLayoutEngine.getInstance().lookupFormattedLayout(
                component,
                Style.EMPTY,
                TextLayoutEngine.COMPUTE_ADVANCES | TextLayoutEngine.COMPUTE_LINE_BOUNDARIES
        );
        var b = new StringBuilder();
        char[] chars = layout.getTextBuf();
        b.append("chars (logical order): ")
                .append(chars.length)
                .append('\n');
        float[] advances = layout.getAdvances();
        b.append("advances (normalized, cluster-based, logical order)\n");
        b.append("LB=line break, GB=grapheme break, NB=non-breaking\n");
        int[] lineBoundaries = layout.getLineBoundaries();
        int lineBoundaryIndex = 0;
        int nextLineBoundary = lineBoundaries[lineBoundaryIndex++];
        for (int i = 0; i < chars.length; ) {
            b.append(String.format(" %04X ", i));
            int lim = Math.min(i + 8, chars.length);
            for (int j = i; j < lim; j++) {
                b.append(String.format("\\u%04X", (int) chars[j]));
            }
            b.append("\n      ");
            for (int j = i; j < lim; j++) {
                b.append(String.format(" %5.1f", advances[j]));
            }
            b.append("\n      ");
            for (int j = i; j < lim; j++) {
                if (j == nextLineBoundary) {
                    b.append("LB    ");
                    nextLineBoundary = lineBoundaries[lineBoundaryIndex++];
                } else if (advances[j] != 0) {
                    b.append("GB    ");
                } else {
                    b.append("NB    ");
                }
            }
            b.append('\n');
            i = lim;
        }

        int[] glyphs = layout.getGlyphs();
        b.append("glyphs (font/slot/glyph, visual order): ")
                .append(glyphs.length)
                .append('\n');
        float[] positions = layout.getPositions();
        byte[] fontIndices = layout.getFontIndices();
        b.append("positions (normalized x/y, visual order)\n");
        int[] glyphFlags = layout.getGlyphFlags();
        b.append("B=bold, I=italic, U=underline, S=strikethrough\n");
        b.append("O=obfuscated, E=color emoji, M=embedded bitmap\n");
        for (int i = 0; i < glyphs.length; ) {
            b.append(String.format(" %04X ", i));
            int lim = Math.min(i + 4, glyphs.length);
            for (int j = i; j < lim; j++) {
                int idx;
                if (fontIndices == null) {
                    idx = 0;
                } else {
                    idx = fontIndices[j] & 0xFF;
                }
                b.append(String.format(" %02X %02X %04X ",
                        idx, glyphs[j] >>> 24, glyphs[j] & 0xFFFF));
            }
            b.append("\n      ");
            for (int j = i; j < lim; j++) {
                b.append(String.format("%6.1f,%4.1f ",
                        positions[j << 1],
                        positions[j << 1 | 1]));
            }
            b.append("\n      ");
            for (int j = i; j < lim; j++) {
                int flag = glyphFlags[j];
                b.append(' ');
                if ((flag & CharacterStyle.BOLD_MASK) != 0) {
                    b.append('B');
                } else {
                    b.append(' ');
                }
                if ((flag & CharacterStyle.ITALIC_MASK) != 0) {
                    b.append('I');
                } else {
                    b.append(' ');
                }
                if ((flag & CharacterStyle.UNDERLINE_MASK) != 0) {
                    b.append('U');
                } else {
                    b.append(' ');
                }
                if ((flag & CharacterStyle.STRIKETHROUGH_MASK) != 0) {
                    b.append('S');
                } else {
                    b.append(' ');
                }
                if ((flag & CharacterStyle.OBFUSCATED_MASK) != 0) {
                    b.append('O');
                } else {
                    b.append(' ');
                }
                if ((flag & CharacterStyle.COLOR_EMOJI_REPLACEMENT) != 0) {
                    b.append('E');
                } else {
                    b.append(' ');
                }
                if ((flag & CharacterStyle.BITMAP_REPLACEMENT) != 0) {
                    b.append('M');
                } else {
                    b.append(' ');
                }
                b.append("    ");
            }
            b.append('\n');
            i = lim;
        }
        Font[] fonts = layout.getFontVector();
        for (int i = 0; i < fonts.length; i++) {
            b.append(String.format(" %02X: %s\n", i, fonts[i].getFamilyName()));
        }
        b.append("total advance: ");
        b.append(layout.getTotalAdvance());
        b.append('\n');

        String result = b.toString();
        source.sendSystemMessage(component);
        source.sendSystemMessage(
                Component.literal(result)
                        .setStyle(Style.EMPTY.withFont(TextLayoutEngine.MONOSPACED))
        );
        Util.ioPool().execute(() -> ModernUI.LOGGER.info(TextLayoutEngine.MARKER, result));
    }
}
