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

package icyllis.modernui.mc;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.arc3d.core.Matrix4;
import icyllis.modernui.graphics.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.util.List;

import static icyllis.arc3d.opengl.GLCore.*;

/**
 * An extension that replaces vanilla tooltip style.
 */
@ApiStatus.Internal
public final class TooltipRenderer {

    // config value
    public static volatile boolean sTooltip = true;

    public static final int[] sFillColor = new int[4];
    public static final int[] sStrokeColor = new int[4];
    public static volatile float sBorderWidth = 4 / 3f;

    // space between mouse and tooltip
    private static final int TOOLTIP_SPACE = 12;
    private static final int H_BORDER = 4;
    private static final int V_BORDER = 4;
    //public static final int LINE_HEIGHT = 10;
    // extra space after first line
    private static final int TITLE_GAP = 2;

    //private static final List<FormattedText> sTempTexts = new ArrayList<>();

    private final FloatBuffer mMatBuf = BufferUtils.createFloatBuffer(16);
    private final Matrix4 mCoreMat = new Matrix4();

    //private static final int[] sActiveFillColor = new int[4];
    private final int[] mActiveStrokeColor = new int[4];
    //static volatile float sAnimationDuration; // milliseconds
    public static volatile int sBorderColorCycle = 1000; // milliseconds

    public static volatile boolean sExactPositioning = true;
    public static volatile boolean sRoundedShapes = true;
    public static volatile boolean sCenterTitle = false;
    public static volatile boolean sTitleBreak = false;

    public volatile boolean mLayoutRTL;

    private boolean mDraw;
    //public static float sAlpha = 1;

    public TooltipRenderer() {
    }

    public void update(long deltaMillis, long timeMillis) {
        /*if (sAnimationDuration <= 0) {
            sAlpha = 1;
        } else if (sDraw) {
            if (sAlpha < 1) {
                sAlpha = Math.min(sAlpha + deltaMillis / sAnimationDuration, 1);
            }
            sDraw = false;
        } else if (sAlpha > 0) {
            sAlpha = Math.max(sAlpha - deltaMillis / sAnimationDuration, 0);
        }*/
        if (mDraw) {
            mDraw = false;
            if (sBorderColorCycle > 0) {
                float p = (timeMillis % sBorderColorCycle) / (float) sBorderColorCycle;
                if (mLayoutRTL) {
                    int pos = (int) ((timeMillis / sBorderColorCycle) & 3);
                    for (int i = 0; i < 4; i++) {
                        mActiveStrokeColor[i] = lerpInLinearSpace(p,
                                sStrokeColor[(i + pos) & 3],
                                sStrokeColor[(i + pos + 1) & 3]);
                    }
                } else {
                    int pos = 3 - (int) ((timeMillis / sBorderColorCycle) & 3);
                    for (int i = 0; i < 4; i++) {
                        mActiveStrokeColor[i] = lerpInLinearSpace(p,
                                sStrokeColor[(i + pos) & 3],
                                sStrokeColor[(i + pos + 3) & 3]);
                    }
                }
            }
        }
    }

    static int lerpInLinearSpace(float fraction, int startValue, int endValue) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            float s = ((startValue >> (i << 3)) & 0xff) / 255.0f;
            float t = ((endValue >> (i << 3)) & 0xff) / 255.0f;
            float v = MathUtil.lerp(s, t, fraction);
            result |= Math.round(v * 255.0f) << (i << 3);
        }
        return result;
    }

    /*public static void drawTooltip(@Nonnull GLCanvas canvas, @Nonnull List<? extends FormattedText> texts,
                                   @Nonnull Font font, @Nonnull ItemStack stack, @Nonnull PoseStack poseStack,
                                   float mouseX, float mouseY, float preciseMouseX, float preciseMouseY,
                                   int maxTextWidth, float screenWidth, float screenHeight,
                                   int framebufferWidth, int framebufferHeight) {
        sDraw = true;
        final float partialX = (preciseMouseX - (int) preciseMouseX);
        final float partialY = (preciseMouseY - (int) preciseMouseY);

        // matrix transformation for x and y params, compatibility to MineColonies
        float tooltipX = mouseX + TOOLTIP_SPACE + partialX;
        float tooltipY = mouseY - TOOLTIP_SPACE + partialY;
        *//*if (mouseX != (int) mouseX || mouseY != (int) mouseY) {
            // ignore partial pixels
            tooltipX += mouseX - (int) mouseX;
            tooltipY += mouseY - (int) mouseY;
        }*//*
        int tooltipWidth = 0;
        int tooltipHeight = V_BORDER * 2;

        for (FormattedText text : texts) {
            tooltipWidth = Math.max(tooltipWidth, font.width(text));
        }

        boolean needWrap = false;
        if (tooltipX + tooltipWidth + H_BORDER + 1 > screenWidth) {
            tooltipX = mouseX - TOOLTIP_SPACE - H_BORDER - 1 - tooltipWidth + partialX;
            if (tooltipX < H_BORDER + 1) {
                if (mouseX > screenWidth / 2) {
                    tooltipWidth = (int) (mouseX - TOOLTIP_SPACE - H_BORDER * 2 - 2);
                } else {
                    tooltipWidth = (int) (screenWidth - TOOLTIP_SPACE - H_BORDER - 1 - mouseX);
                }
                needWrap = true;
            }
        }

        if (maxTextWidth > 0 && tooltipWidth > maxTextWidth) {
            tooltipWidth = maxTextWidth;
            needWrap = true;
        }

        int titleLinesCount = 1;
        if (needWrap) {
            int w = 0;
            final List<FormattedText> temp = sTempTexts;
            for (int i = 0; i < texts.size(); i++) {
                List<FormattedText> wrapped = font.getSplitter().splitLines(texts.get(i), tooltipWidth, Style.EMPTY);
                if (i == 0) {
                    titleLinesCount = wrapped.size();
                }
                for (FormattedText text : wrapped) {
                    w = Math.max(w, font.width(text));
                    temp.add(text);
                }
            }
            tooltipWidth = w;
            texts = temp;

            if (mouseX > screenWidth / 2) {
                tooltipX = mouseX - TOOLTIP_SPACE - H_BORDER - 1 - tooltipWidth + partialX;
            } else {
                tooltipX = mouseX + TOOLTIP_SPACE + partialX;
            }
        }

        if (texts.size() > 1) {
            tooltipHeight += (texts.size() - 1) * LINE_HEIGHT;
            if (texts.size() > titleLinesCount) {
                tooltipHeight += TITLE_GAP;
            }
        }

        tooltipY = MathUtil.clamp(tooltipY, V_BORDER + 1, screenHeight - tooltipHeight - V_BORDER - 1);

        // smoothing scaled pixels, keep the same partial value as mouse position since tooltipWidth and height are int
        final int tooltipLeft = (int) tooltipX;
        final int tooltipTop = (int) tooltipY;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        poseStack.pushPose();
        poseStack.translate(0, 0, 400); // because of the order of draw calls, we actually don't need z-shifting
        final Matrix4f mat = poseStack.last().pose();

        final int oldVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        final int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);

        // give some points to the original framebuffer, not gui scaled
        canvas.reset(framebufferWidth, framebufferHeight);

        // swap matrices
        RenderSystem.getProjectionMatrix().store(sMatBuf.rewind());
        sMyMat.set(sMatBuf.rewind());
        canvas.setProjection(sMyMat);

        canvas.save();
        RenderSystem.getModelViewMatrix().store(sMatBuf.rewind());
        sMyMat.set(sMatBuf.rewind());
        canvas.multiply(sMyMat);

        mat.store(sMatBuf.rewind()); // Sodium check the remaining
        sMyMat.set(sMatBuf.rewind());
        //myMat.translate(0, 0, -2000);
        canvas.multiply(sMyMat);

        Paint paint = Paint.take();

        paint.setSmoothRadius(0.5f);

        for (int i = 0; i < 4; i++) {
            int color = sFillColor[i];
            int alpha = (int) ((color >>> 24) * sAlpha);
            sColor[i] = (color & 0xFFFFFF) | (alpha << 24);
        }
        paint.setColors(sColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(tooltipX - H_BORDER, tooltipY - V_BORDER,
                tooltipX + tooltipWidth + H_BORDER,
                tooltipY + tooltipHeight + V_BORDER, 3, paint);

        for (int i = 0; i < 4; i++) {
            int color = sStrokeColor[i];
            int alpha = (int) ((color >>> 24) * sAlpha);
            sColor[i] = (color & 0xFFFFFF) | (alpha << 24);
        }
        paint.setColors(sColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        canvas.drawRoundRect(tooltipX - H_BORDER, tooltipY - V_BORDER,
                tooltipX + tooltipWidth + H_BORDER,
                tooltipY + tooltipHeight + V_BORDER, 3, paint);
        *//*canvas.drawRoundedFrameT1(tooltipX - H_BORDER, tooltipY - V_BORDER,
                tooltipX + tooltipWidth + H_BORDER, tooltipY + tooltipHeight + V_BORDER, 3);*//*

        canvas.restore();
        canvas.draw(null);

        glBindVertexArray(oldVertexArray);
        glUseProgram(oldProgram);

        final MultiBufferSource.BufferSource source =
                MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        final int color = (Math.max((int) (sAlpha * 255), 1) << 24) | 0xFFFFFF;
        for (int i = 0; i < texts.size(); i++) {
            FormattedText text = texts.get(i);
            if (text != null)
                ModernFontRenderer.drawText(text, tooltipX, tooltipY, color, true, mat, source,
                        false, 0, LightTexture.FULL_BRIGHT);
            if (i + 1 == titleLinesCount) {
                tooltipY += TITLE_GAP;
            }
            tooltipY += LINE_HEIGHT;
        }
        source.endBatch();

        // because of the order of draw calls, we actually don't need z-shifting
        poseStack.translate(partialX, partialY, -400);
        // compatibility with Forge mods, like Quark
        *//*MinecraftForge.EVENT_BUS.post(new RenderTooltipEvent.PostText(stack, texts, poseStack, tooltipLeft,
        tooltipTop,
                font, tooltipWidth, tooltipHeight));*//*
        poseStack.popPose();

        RenderSystem.enableDepthTest();
        sTempTexts.clear();
    }*/

    int chooseBorderColor(int corner) {
        if (sBorderColorCycle > 0) {
            return mActiveStrokeColor[corner];
        } else {
            return sStrokeColor[corner];
        }
    }

    public void drawTooltip(@Nonnull GLSurfaceCanvas canvas, @Nonnull Window window,
                            @Nonnull ItemStack itemStack, @Nonnull GuiGraphics gr,
                            @Nonnull List<ClientTooltipComponent> list, int mouseX, int mouseY,
                            @Nonnull Font font, int screenWidth, int screenHeight,
                            float partialX, float partialY, @Nullable ClientTooltipPositioner positioner) {
        mDraw = true;

        int tooltipWidth;
        int tooltipHeight;
        boolean titleGap = true;
        int titleBreakHeight = 0;
        if (list.size() == 1) {
            ClientTooltipComponent component = list.get(0);
            tooltipWidth = component.getWidth(font);
            tooltipHeight = component.getHeight() - TITLE_GAP;
        } else {
            tooltipWidth = 0;
            tooltipHeight = 0;
            for (int i = 0; i < list.size(); i++) {
                ClientTooltipComponent component = list.get(i);
                tooltipWidth = Math.max(tooltipWidth, component.getWidth(font));
                int componentHeight = component.getHeight();
                tooltipHeight += componentHeight;
                if (i == 0 && !itemStack.isEmpty() &&
                        component instanceof ClientTextTooltip) {
                    titleGap = true;
                    titleBreakHeight = componentHeight;
                }
            }
            if (!titleGap) {
                tooltipHeight -= TITLE_GAP;
            }
        }

        float tooltipX;
        float tooltipY;
        if (positioner != null) {
            var pos = positioner.positionTooltip(screenWidth, screenHeight,
                    mouseX, mouseY,
                    tooltipWidth, tooltipHeight);
            tooltipX = pos.x();
            tooltipY = pos.y();
        } else {
            if (mLayoutRTL) {
                tooltipX = mouseX + TOOLTIP_SPACE + partialX - 24 - tooltipWidth;
                if (tooltipX - partialX < 4) {
                    tooltipX += 24 + tooltipWidth;
                }
            } else {
                tooltipX = mouseX + TOOLTIP_SPACE + partialX;
                if (tooltipX - partialX + tooltipWidth + 4 > screenWidth) {
                    tooltipX -= 28 + tooltipWidth;
                }
            }
            partialX = (tooltipX - (int) tooltipX);

            tooltipY = mouseY - TOOLTIP_SPACE + partialY;
            if (tooltipY + tooltipHeight + 6 > screenHeight) {
                tooltipY = screenHeight - tooltipHeight - 6;
            }
            partialY = (tooltipY - (int) tooltipY);
        }

        // we should disable depth test, because texts may be translucent
        // for compatibility reasons, we keep this enabled, and it doesn't seem to be a big problem
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        gr.pose().pushPose();
        // because of the order of draw calls, we actually don't need z-shifting
        gr.pose().translate(0, 0, 400);
        final Matrix4f pose = gr.pose().last().pose();

        final int oldVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        final int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);

        // give some points to the original framebuffer, not gui scaled
        canvas.reset(window.getWidth(), window.getHeight());

        // swap matrices
        RenderSystem.getProjectionMatrix().get(mMatBuf.rewind());
        mCoreMat.set(mMatBuf.rewind());
        canvas.setProjection(mCoreMat);

        canvas.save();
        RenderSystem.getModelViewMatrix().get(mMatBuf.rewind());
        mCoreMat.set(mMatBuf.rewind());
        canvas.concat(mCoreMat);

        pose.get(mMatBuf.rewind());
        mCoreMat.set(mMatBuf.rewind());
        // RenderSystem.getModelViewMatrix() has Z translation normalized to -1
        // We have to offset against our canvas Z translation, see restore matrix in GLCanvas
        mCoreMat.preTranslate(0, 0, 3000);
        canvas.concat(mCoreMat);

        Paint paint = Paint.obtain();

        /*for (int i = 0; i < 4; i++) {
            int color = sFillColor[i];
            int alpha = (int) ((color >>> 24) * sAlpha + 0.5f);
            sActiveFillColor[i] = (color & 0xFFFFFF) | (alpha << 24);
        }*/
        paint.setStyle(Paint.FILL);
        if (sRoundedShapes) {
            canvas.drawRoundRectGradient(tooltipX - H_BORDER, tooltipY - V_BORDER,
                    tooltipX + tooltipWidth + H_BORDER,
                    tooltipY + tooltipHeight + V_BORDER,
                    sFillColor[0], sFillColor[1],
                    sFillColor[2], sFillColor[3],
                    3, paint);
        } else {
            canvas.drawRectGradient(tooltipX - H_BORDER + 1, tooltipY - V_BORDER + 1,
                    tooltipX + tooltipWidth + H_BORDER - 1,
                    tooltipY + tooltipHeight + V_BORDER - 1,
                    sFillColor[0], sFillColor[1],
                    sFillColor[2], sFillColor[3],
                    paint);
        }

        if (titleGap && sTitleBreak) {
            paint.setColor(0xE0C8C8C8);
            paint.setStrokeWidth(1f);
            canvas.drawLine(tooltipX, tooltipY + titleBreakHeight,
                    tooltipX + tooltipWidth, tooltipY + titleBreakHeight,
                    paint);
        }

        /*for (int i = 0; i < 4; i++) {
            int color = sStrokeColor[i];
            int alpha = (int) ((color >>> 24) * sAlpha + 0.5f);
            sActiveStrokeColor[i] = (color & 0xFFFFFF) | (alpha << 24);
        }*/
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(sBorderWidth);
        if (sRoundedShapes) {
            canvas.drawRoundRectGradient(tooltipX - H_BORDER, tooltipY - V_BORDER,
                    tooltipX + tooltipWidth + H_BORDER,
                    tooltipY + tooltipHeight + V_BORDER,
                    chooseBorderColor(0), chooseBorderColor(1),
                    chooseBorderColor(2), chooseBorderColor(3),
                    3, paint);
        } else {
            canvas.drawRectGradient(tooltipX - H_BORDER + 1, tooltipY - V_BORDER + 1,
                    tooltipX + tooltipWidth + H_BORDER - 1,
                    tooltipY + tooltipHeight + V_BORDER - 1,
                    chooseBorderColor(0), chooseBorderColor(1),
                    chooseBorderColor(2), chooseBorderColor(3),
                    paint);
        }

        paint.recycle();

        canvas.restore();
        canvas.executeRenderPass(null);

        glBindVertexArray(oldVertexArray);
        glUseProgram(oldProgram);

        final int drawX = (int) tooltipX;
        int drawY = (int) tooltipY;

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        final MultiBufferSource.BufferSource source = gr.bufferSource();
        gr.pose().translate(partialX, partialY, 0);
        for (int i = 0; i < list.size(); i++) {
            ClientTooltipComponent component = list.get(i);
            if (titleGap && i == 0 && sCenterTitle) {
                component.renderText(font, drawX + (tooltipWidth - component.getWidth(font)) / 2, drawY, pose, source);
            } else if (mLayoutRTL) {
                component.renderText(font, drawX + tooltipWidth - component.getWidth(font), drawY, pose, source);
            } else {
                component.renderText(font, drawX, drawY, pose, source);
            }
            if (titleGap && i == 0) {
                drawY += TITLE_GAP;
            }
            drawY += component.getHeight();
        }
        gr.flush();

        drawY = (int) tooltipY;

        for (int i = 0; i < list.size(); i++) {
            ClientTooltipComponent component = list.get(i);
            if (mLayoutRTL) {
                component.renderImage(font, drawX + tooltipWidth - component.getWidth(font), drawY, gr);
            } else {
                component.renderImage(font, drawX, drawY, gr);
            }
            if (titleGap && i == 0) {
                drawY += TITLE_GAP;
            }
            drawY += component.getHeight();
        }
        gr.pose().popPose();
    }
}
