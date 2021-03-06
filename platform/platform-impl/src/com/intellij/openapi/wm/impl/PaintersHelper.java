/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ImageLoader;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.VolatileImage;
import java.io.File;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class PaintersHelper implements Painter.Listener {
  private final Set<Painter> myPainters = ContainerUtil.newLinkedHashSet();
  private final Map<Painter, Component> myPainter2Component = ContainerUtil.newLinkedHashMap();

  private final JComponent myRootComponent;

  public PaintersHelper(@NotNull JComponent component) {
    myRootComponent = component;
  }

  public boolean hasPainters() {
    return !myPainters.isEmpty();
  }

  public void addPainter(@NotNull Painter painter, @Nullable Component component) {
    myPainters.add(painter);
    myPainter2Component.put(painter, component == null ? myRootComponent : component);
    painter.addListener(this);
  }

  public void removePainter(@NotNull Painter painter) {
    painter.removeListener(this);
    myPainters.remove(painter);
    myPainter2Component.remove(painter);
  }

  public void clear() {
    for (Painter painter : myPainters) {
      painter.removeListener(this);
    }
    myPainters.clear();
    myPainter2Component.clear();
  }

  public void paint(Graphics g) {
    paint(g, myRootComponent);
  }

  public void paint(Graphics g, JComponent current) {
    if (myPainters.isEmpty()) return;
    Rectangle clip = ObjectUtils.notNull(g.getClipBounds(), current.getBounds());

    Graphics2D g2d = (Graphics2D)g;
    for (Painter painter : myPainters) {
      Component component = myPainter2Component.get(painter);
      if (component.getParent() == null) continue;
      Rectangle componentBounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), current);

      if (!painter.needsRepaint()) {
        continue;
      }

      if (clip.contains(componentBounds) || clip.intersects(componentBounds)) {
        Point targetPoint = SwingUtilities.convertPoint(current, 0, 0, component);
        Rectangle targetRect = new Rectangle(targetPoint, component.getSize());
        g2d.setClip(clip.intersection(componentBounds));
        g2d.translate(-targetRect.x, -targetRect.y);
        painter.paint(component, g2d);
        g2d.translate(targetRect.x, targetRect.y);
      }
    }

  }

  @Override
  public void onNeedsRepaint(Painter painter, JComponent dirtyComponent) {
    if (dirtyComponent != null && dirtyComponent.isShowing()) {
      Rectangle rec = SwingUtilities.convertRectangle(dirtyComponent, dirtyComponent.getBounds(), myRootComponent);
      myRootComponent.repaint(rec);
    }
    else {
      myRootComponent.repaint();
    }
  }

  public enum FillType {
    BG_CENTER, TILE, SCALE,
    CENTER, TOP_CENTER, BOTTOM_CENTER,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
  }

  public static AbstractPainter newWallpaperPainter(final String propertyName) {
    return new ImagePainter() {
      Image image;
      float alpha;
      Insets insets;
      FillType fillType;

      String current;

      @Override
      public void executePaint(Component component, Graphics2D g) {
        String value = StringUtil.notNullize(System.getProperty(propertyName), propertyName + ".png");
        if (!Comparing.equal(value, current)) {
          current = value;
          image = scaled = null;
          insets = JBUI.emptyInsets();
          String[] parts = value.split(",");
          try {
            alpha = StringUtil.parseInt(parts.length > 1 ? parts[1]: "", 10) / 100f;
            try {
              fillType =  FillType.valueOf(parts.length > 2 ? parts[2].toUpperCase(Locale.ENGLISH) : "");
            }
            catch (IllegalArgumentException e) {
              fillType = FillType.SCALE;
            }
            String filePath = parts[0];

            URL url = filePath.contains("://") ? new URL(filePath) :
                      (FileUtil.isAbsolutePlatformIndependent(filePath)
                       ? new File(filePath)
                       : new File(PathManager.getConfigPath(), filePath)).toURI().toURL();
            image = ImageLoader.loadFromUrl(url);
          }
          catch (Exception ignored) {
          }
        }
        if (image == null) return;
        executePaint(g, component, image, fillType, alpha, insets);
      }
    };
  }

  public static AbstractPainter newImagePainter(final Image image, final FillType fillType, final float alpha, final Insets insets) {
    return new ImagePainter() {
      @Override
      public void executePaint(Component component, Graphics2D g) {
        executePaint(g, component, image, fillType, alpha, insets);
      }
    };
  }

  private abstract static class ImagePainter extends AbstractPainter {

    VolatileImage scaled;

    @Override
    public boolean needsRepaint() { return true; }

    public void executePaint(Graphics2D g, Component component, Image image, FillType fillType, float alpha, Insets insets) {
      int cw0 = component.getWidth();
      int ch0 = component.getHeight();
      Insets i = JBUI.insets(insets.top * ch0 / 100, insets.left * cw0 / 100, insets.bottom * ch0 / 100, insets.right * cw0 / 100);
      int cw = cw0 - i.left - i.right;
      int ch = ch0 - i.top - i.bottom;
      int w = image.getWidth(null);
      int h = image.getHeight(null);
      if (w <= 0 || h <= 0) return;

      if (fillType == FillType.SCALE) {
        int sw0 = scaled == null ? -1 : scaled.getWidth(null);
        int sh0 = scaled == null ? -1 : scaled.getHeight(null);
        boolean useWidth = cw * h > ch * w;
        int sw = useWidth ? cw : w * ch / h;
        int sh = useWidth ? h * cw / w : ch;
        if (sw0 != sw || sh0 != sh || scaled != null && scaled.contentsLost()) {
          if (sw0 != sw || sh0 != sh || scaled == null) {
            scaled = createImage(g, sw, sh);
          }
          Graphics2D gg = scaled.createGraphics();
          gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                              RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          gg.setComposite(AlphaComposite.Src);
          gg.drawImage(image, 0, 0, sw, sh, null);
          gg.dispose();
        }
        w = sw;
        h = sh;
      }
      else if (scaled == null || scaled.contentsLost()) {
        if (scaled == null) {
          scaled = createImage(g, w, h);
        }
        Graphics2D gg = scaled.createGraphics();
        gg.setComposite(AlphaComposite.Src);
        gg.drawImage(image, 0, 0, null);
        gg.dispose();
      }

      GraphicsConfig cfg = new GraphicsConfig(g).setAlpha(alpha);
      g.setColor(g.getBackground());
      if (fillType == FillType.CENTER || fillType == FillType.BG_CENTER || fillType == FillType.SCALE ||
          fillType == FillType.TOP_CENTER || fillType == FillType.BOTTOM_CENTER) {
        int x = i.left + (cw - w) / 2;
        int y = fillType == FillType.TOP_CENTER? i.top :
                fillType == FillType.BOTTOM_CENTER? ch0 - i.bottom - h :
                i.top + (ch - h) / 2;
        UIUtil.drawImage(g, scaled, x, y, null);
        if (fillType == FillType.BG_CENTER) {
          g.setColor(component.getBackground());
          g.fillRect(0, 0, x, ch0);
          g.fillRect(x, 0, w, h);
          g.fillRect(x + w, 0, x, ch0);
          g.fillRect(x, y + h, w, y);
        }
      }
      else if (fillType == FillType.TOP_LEFT || fillType == FillType.TOP_RIGHT ||
               fillType == FillType.BOTTOM_LEFT || fillType == FillType.BOTTOM_RIGHT) {
        int x = fillType == FillType.TOP_LEFT || fillType == FillType.BOTTOM_LEFT ? i.left : cw0 - i.right - w;
        int y = fillType == FillType.TOP_LEFT || fillType == FillType.TOP_RIGHT ? i.top : ch0 - i.bottom - h;
        UIUtil.drawImage(g, scaled, x, y, null);
      }
      else if (fillType == FillType.TILE) {
        for (int x = i.left; x < cw; x+=w)
          for (int y = i.top; y < ch; y+=h)
            UIUtil.drawImage(g, scaled, x, y, null);
      }
      cfg.restore();
    }

    @NotNull
    private static VolatileImage createImage(Graphics2D g, int w, int h) {
      GraphicsConfiguration configuration = g.getDeviceConfiguration();
      try {
        return configuration.createCompatibleVolatileImage(w, h, new ImageCapabilities(true), Transparency.TRANSLUCENT);
      }
      catch (Exception e) {
        return configuration.createCompatibleVolatileImage(w, h, Transparency.TRANSLUCENT);
      }
    }
  }
}
