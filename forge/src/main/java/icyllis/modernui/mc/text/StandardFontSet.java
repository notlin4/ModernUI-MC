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

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import icyllis.modernui.graphics.text.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Unmodifiable;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.*;

/**
 * This class is used only for <b>compatibility</b>.
 * <p>
 * Some mods have it own {@link net.minecraft.client.gui.Font.StringRenderOutput},
 * we have to provide per-code-point glyph info. Minecraft vanilla maps code points
 * to glyphs without text shaping (no international support). It also ignores
 * resolution level (GUI scale), we assume it's current and round it up. Vanilla
 * doesn't support FreeType embolden as well, we ignore it.
 * <p>
 * This class is similar to {@link FontCollection} but no font itemization.
 * <p>
 * We use our own font atlas and rectangle packing algorithm.
 *
 * @author BloCamLimb
 * @since 3.8
 */
public class StandardFontSet extends FontSet {

    @Unmodifiable
    private List<FontFamily> mFamilies = Collections.emptyList();

    private Int2ObjectOpenHashMap<BakedGlyph> mGlyphs;

    private final IntFunction<BakedGlyph> mCacheGlyph = this::cacheGlyph;

    private Int2ObjectOpenHashMap<GlyphInfo> mGlyphInfos;

    private final IntFunction<GlyphInfo> mCacheGlyphInfo = this::cacheGlyphInfo;

    private float mResLevel = 2;
    private final FontPaint mStandardPaint = new FontPaint();

    public StandardFontSet(@Nonnull TextureManager texMgr,
                           @Nonnull ResourceLocation fontName) {
        super(texMgr, fontName); // <- unused

        mStandardPaint.setFontStyle(FontPaint.NORMAL);
        mStandardPaint.setLocale(Locale.ROOT);
    }

    public void reload(@Nonnull FontCollection fontCollection, int newResLevel) {
        super.reload(Collections.emptyList());
        mFamilies = fontCollection.getFamilies();
        invalidateCache(newResLevel);
    }

    public void invalidateCache(int newResLevel) {
        if (mGlyphs != null) {
            mGlyphs.clear();
        }
        if (mGlyphInfos != null) {
            mGlyphInfos.clear();
        }
        int fontSize = TextLayoutProcessor.computeFontSize(newResLevel);
        mStandardPaint.setFontSize(fontSize);
        mResLevel = newResLevel;
    }

    @Nonnull
    private GlyphInfo cacheGlyphInfo(int codePoint) {
        for (FontFamily family : mFamilies) {
            if (!family.hasGlyph(codePoint)) {
                continue;
            }
            Font font = family.getClosestMatch(FontPaint.NORMAL);
            // we must check BitmapFont first,
            // because codePoint may be an invalid Unicode code point
            if (font instanceof BitmapFont bitmapFont) {
                var glyph = bitmapFont.getGlyphInfo(codePoint);
                if (glyph != null) {
                    return glyph;
                }
            } else if (font instanceof SpaceFont spaceFont) {
                float adv = spaceFont.getAdvance(codePoint);
                if (!Float.isNaN(adv)) {
                    return (GlyphInfo.SpaceGlyphInfo) () -> adv;
                }
            } else if (font instanceof OutlineFont outlineFont) {
                // no variation selector
                if (outlineFont.hasGlyph(codePoint, 0)) {
                    char[] chars = Character.toChars(codePoint);
                    float adv = outlineFont.doSimpleLayout(
                            chars,
                            0, chars.length,
                            mStandardPaint, null, null,
                            0, 0);
                    return new StandardGlyphInfo((int) (adv / mResLevel + 0.95f));
                }
            }
        }
        return SpecialGlyphs.MISSING;
    }

    @Nonnull
    @Override
    public GlyphInfo getGlyphInfo(int codePoint, boolean notFishy) {
        if (mGlyphInfos == null) {
            mGlyphInfos = new Int2ObjectOpenHashMap<>();
        }
        return mGlyphInfos.computeIfAbsent(codePoint, mCacheGlyphInfo);
    }

    @Nonnull
    private BakedGlyph cacheGlyph(int codePoint) {
        for (FontFamily family : mFamilies) {
            if (!family.hasGlyph(codePoint)) {
                continue;
            }
            Font font = family.getClosestMatch(FontPaint.NORMAL);
            // we MUST check BitmapFont first,
            // because codePoint may be an invalid Unicode code point
            // but vanilla doesn't validate that
            if (font instanceof BitmapFont bitmapFont) {
                // auto bake
                var glyph = bitmapFont.getGlyph(codePoint);
                if (glyph != null) {
                    // convert to Minecraft, bearing Y is 3, see SheetGlyphInfo
                    float up = 3F + bitmapFont.getAscent() +
                            (float) glyph.y / TextLayoutEngine.BITMAP_SCALE;
                    float left = (float) glyph.x / TextLayoutEngine.BITMAP_SCALE;
                    float right = (float) (glyph.x + glyph.width) / TextLayoutEngine.BITMAP_SCALE;
                    float down = up + (float) glyph.height / TextLayoutEngine.BITMAP_SCALE;
                    return new StandardBakedGlyph(
                            bitmapFont::getCurrentTexture,
                            glyph.u1,
                            glyph.u2,
                            glyph.v1,
                            glyph.v2,
                            left,
                            right,
                            up,
                            down
                    );
                }
            } else if (font instanceof SpaceFont) {
                return EmptyGlyph.INSTANCE;
            } else if (font instanceof OutlineFont outlineFont) {
                char[] chars = Character.toChars(codePoint);
                IntArrayList glyphs = new IntArrayList(1);
                float adv = outlineFont.doSimpleLayout(
                        chars,
                        0, chars.length,
                        mStandardPaint, glyphs, null,
                        0, 0
                );
                if (glyphs.size() == 1 &&
                        glyphs.getInt(0) != 0) { // 0 is the missing glyph for TTF
                    // bake glyph ourselves
                    var glyph = TextLayoutEngine.getInstance().lookupGlyph(
                            outlineFont,
                            mStandardPaint.getFontSize(),
                            glyphs.getInt(0)
                    );
                    if (glyph != null) {
                        // convert to Minecraft, bearing Y is 3, see SheetGlyphInfo
                        float up = 3F + TextLayout.STANDARD_BASELINE_OFFSET + glyph.y / mResLevel;
                        float left = glyph.x / mResLevel;
                        float right = (glyph.x + glyph.width) / mResLevel;
                        float down = up + glyph.height / mResLevel;
                        return new StandardBakedGlyph(
                                () -> TextLayoutEngine.getInstance().getStandardTexture(), // <- singleton
                                glyph.u1,
                                glyph.u2,
                                glyph.v1,
                                glyph.v2,
                                left,
                                right,
                                up,
                                down
                        );
                    }
                }
                if (adv > 0) {
                    // no pixels, e.g. space
                    return EmptyGlyph.INSTANCE;
                }
            }
            // color emoji requires complex layout, so no support
        }
        return super.getGlyph(codePoint); // missing
    }

    @Nonnull
    @Override
    public BakedGlyph getGlyph(int codePoint) {
        if (mGlyphs == null) {
            mGlyphs = new Int2ObjectOpenHashMap<>();
        }
        return mGlyphs.computeIfAbsent(codePoint, mCacheGlyph);
    }

    // no obfuscated support

    public static class StandardGlyphInfo implements GlyphInfo {

        private final float mAdvance;

        public StandardGlyphInfo(int advance) {
            mAdvance = advance;
        }

        @Override
        public float getAdvance() {
            return mAdvance;
        }

        @Override
        public float getBoldOffset() {
            return 0.5f;
        }

        @Override
        public float getShadowOffset() {
            return ModernTextRenderer.sShadowOffset;
        }

        @Nonnull
        @Override
        public BakedGlyph bake(@Nonnull Function<SheetGlyphInfo, BakedGlyph> function) {
            return EmptyGlyph.INSTANCE;
        }
    }

    public static class StandardBakedGlyph extends BakedGlyph {

        private static final RenderType TYPE = RenderType.text(new ResourceLocation(""));
        private static final RenderType TYPE_SEE_THROUGH = RenderType.textSeeThrough(new ResourceLocation(""));
        private static final RenderType TYPE_POLYGON_OFFSET = RenderType.textPolygonOffset(new ResourceLocation(""));

        // OpenGL texture ID can be changing
        private final IntSupplier mCurrentTexture;

        public StandardBakedGlyph(IntSupplier currentTexture,
                                  float u0, float u1, float v0, float v1,
                                  float left, float right, float up, float down) {
            super(TYPE, TYPE_SEE_THROUGH, TYPE_POLYGON_OFFSET,
                    u0, u1, v0, v1,
                    left, right, up, down);
            mCurrentTexture = currentTexture;
        }

        @Nonnull
        @Override
        public RenderType renderType(
                @Nonnull net.minecraft.client.gui.Font.DisplayMode mode) {
            return TextRenderType.getOrCreate(
                    mCurrentTexture.getAsInt(),
                    mode
            );
        }
    }
}
