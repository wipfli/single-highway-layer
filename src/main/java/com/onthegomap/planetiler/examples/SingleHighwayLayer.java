package com.onthegomap.planetiler.examples;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.Profile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.util.ZoomFunction;

import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;

public class SingleHighwayLayer implements Profile {

  private String[] highwayCategories = {
    "unclassified",
    "tertiary",
    "secondary",
    "primary",
    "trunk",
    "motorway",
  };

  int tunnelAndBridgeMinZoom = 14;
  int linkMinZoom = 11;
  int globalMaxZoom = 14;

  private boolean isTunnel(SourceFeature sourceFeature) {
    return sourceFeature.hasTag("tunnel", "yes", "building_passage") || sourceFeature.hasTag("covered", "yes");
  }

  private boolean isBridge(SourceFeature sourceFeature) {
    return sourceFeature.hasTag("bridge");
  }

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    return null;
  }

  class LineWidth implements ZoomFunction<Number> {
    boolean isCasing;
    boolean isLink;
    double[] levels;

    public LineWidth(boolean isCasing, boolean isLink, double[] levels) {
      this.isCasing = isCasing;
      this.isLink = isLink;
      this.levels = levels;
    }
    @Override
    public Number apply(int value) {
      double width = levels[value];
      if (isCasing) {
        width += 2.0;
      }
      if (isLink) {
        width -= 1.5;
      }
      return width;
    }
  }

  class LineSortKey implements ZoomFunction<Number> {
    int categoryIndex;
    boolean isLink;
    boolean isBridge;
    boolean isTunnel;
    Integer layer;
    boolean isCasing;

    public LineSortKey(int categoryIndex, boolean isLink, boolean isBridge, boolean isTunnel, Integer layer, boolean isCasing) {
      this.categoryIndex = categoryIndex; 
      this.isLink = isLink; 
      this.isBridge = isBridge; 
      this.isTunnel = isTunnel; 
      this.layer = layer;
      this.isCasing = isCasing;
    }
    @Override
    public Number apply(int value) {
      int result = 0;
      if (value < 14) {
        result += 2 * categoryIndex + (isCasing ? 0 : 1) + (isLink ? 0 : 2 * highwayCategories.length);
      }
      else {
        result += categoryIndex + (isCasing ? 0 : 1) * 2 * highwayCategories.length + (isLink ? 0 : highwayCategories.length);
      }

      if (14 <= value) {
        if (isBridge) {
          result += (layer == null ? 1 : layer) * 4 * highwayCategories.length;
        }
        if (isTunnel) {
          result += (layer == null ? -1 : layer) * 4 * highwayCategories.length;
        }
      }
      return result;
    }
  }

  class LineColor implements ZoomFunction<String> {
    boolean isTunnel;
    boolean isCasing;
    String[] levels;

    public LineColor(boolean isTunnel, boolean isCasing, String[] levels) {
      this.isTunnel = isTunnel;
      this.isCasing = isCasing;
      this.levels = levels;
    }

    @Override
    public String apply(int value) {
      if (value < 14) {
        return levels[value];
      }
      else {
        if (isTunnel) {
          return isCasing ? "#C5C5C5" : "#e4e4e4";
        }
        else {
          return levels[value];
        }
      }
    }
  }

  @Override
  public void processFeature(SourceFeature sourceFeature, FeatureCollector features) {

    if (sourceFeature.canBeLine() && sourceFeature.hasTag("highway", "unclassified")) {
      int categoryIndex = Arrays.asList(highwayCategories).indexOf("unclassified");
      boolean isLink = false;
      boolean isTunnel = isTunnel(sourceFeature);
      boolean isBridge = isBridge(sourceFeature);
      
      int minZoom = 12;
      int maxZoom = globalMaxZoom;
      Integer layer = sourceFeature.hasTag("layer") ? Integer.parseInt(sourceFeature.getTag("layer").toString()) : null;

      double[] lineWidthLevels = {
        0.0, // z0
        0.0, // z1
        0.0, // z2
        0.0, // z3
        0.0, // z4
        0.0, // z5
        0.0, // z6
        0.0, // z7
        0.0, // z8
        0.0, // z9
        0.0, // z10
        0.0, // z11
        1.0, // z12
        1.5, // z13
        2.0, // z14
      };

      String[] casingLineColorLevels = {
        "", // z0
        "", // z1
        "", // z2
        "", // z3
        "", // z4
        "", // z5
        "", // z6
        "", // z7
        "", // z8
        "", // z9
        "", // z10
        "", // z11
        "#ccc", // z12
        "#ccc", // z13
        "#ccc", // z14
      };

      String[] lineColorLevels = {
        "", // z0
        "", // z1
        "", // z2
        "", // z3
        "", // z4
        "", // z5
        "", // z6
        "", // z7
        "", // z8
        "", // z9
        "", // z10
        "", // z11
        "white", // z12
        "white", // z13
        "white", // z14
      };

      features.line("highway")
        .setMinPixelSize(0)
        .setMinZoom(minZoom)
        .setMaxZoom(maxZoom)
        .setAttr("line-sort-key", new LineSortKey(categoryIndex, isLink, isBridge, isTunnel, layer, true))
        .setAttr("line-color", new LineColor(isTunnel, true, casingLineColorLevels))
        .setAttr("line-width", new LineWidth(true, isLink, lineWidthLevels))
        .setAttr("line-width-z20", 5 * (lineWidthLevels[14] - (isLink ? 1.5 : 0)) + 2);
      
      features.line("highway")
        .setMinPixelSize(0)
        .setMinZoom(minZoom)
        .setMaxZoom(maxZoom)
        .setAttr("line-sort-key", new LineSortKey(categoryIndex, isLink, isBridge, isTunnel, layer, false))
        .setAttr("line-color", new LineColor(isTunnel, false, lineColorLevels))
        .setAttr("line-width", new LineWidth(false, isLink, lineWidthLevels))
        .setAttr("line-width-z20", 5 * (lineWidthLevels[14] - (isLink ? 1.5 : 0)));
    }

    if (sourceFeature.canBeLine() && sourceFeature.hasTag("highway", "tertiary", "tertiary_link")) {

      int categoryIndex = Arrays.asList(highwayCategories).indexOf("tertiary");
      boolean isLink = sourceFeature.getTag("highway").toString().endsWith("_link");
      boolean isTunnel = isTunnel(sourceFeature);
      boolean isBridge = isBridge(sourceFeature);
      int minZoom = isLink ? 14 : 10;
      int maxZoom = globalMaxZoom;
      Integer layer = sourceFeature.hasTag("layer") ? Integer.parseInt(sourceFeature.getTag("layer").toString()) : null;

      double[] lineWidthLevels = {
        0.0, // z0
        0.0, // z1
        0.0, // z2
        0.0, // z3
        0.0, // z4
        0.0, // z5
        0.0, // z6
        0.0, // z7
        0.0, // z8
        0.0, // z9
        1.0, // z10
        1.0, // z11
        2.0, // z12
        2.5, // z13
        3.0, // z14
      };

      String[] casingLineColorLevels = {
        "", // z0
        "", // z1
        "", // z2
        "", // z3
        "", // z4
        "", // z5
        "", // z6
        "", // z7
        "", // z8
        "", // z9
        "#ccc", // z10
        "#ccc", // z11
        "#bbb", // z12
        "#bbb", // z13
        "#bbb", // z14
      };

      String[] lineColorLevels = {
        "", // z0
        "", // z1
        "", // z2
        "", // z3
        "", // z4
        "", // z5
        "", // z6
        "", // z7
        "", // z8
        "", // z9
        "white", // z10
        "white", // z11
        "white", // z12
        "white", // z13
        "white", // z14
      };

      features.line("highway")
        .setMinPixelSize(0)
        .setMinZoom(minZoom)
        .setMaxZoom(maxZoom)
        .setAttr("line-sort-key", new LineSortKey(categoryIndex, isLink, isBridge, isTunnel, layer, true))
        .setAttr("line-color", new LineColor(isTunnel, true, casingLineColorLevels))
        .setAttr("line-width", new LineWidth(true, isLink, lineWidthLevels))
        .setAttr("line-width-z20", 5 * (lineWidthLevels[14] - (isLink ? 1.5 : 0)) + 2);
      
      features.line("highway")
        .setMinPixelSize(0)
        .setMinZoom(minZoom)
        .setMaxZoom(maxZoom)
        .setAttr("line-sort-key", new LineSortKey(categoryIndex, isLink, isBridge, isTunnel, layer, false))
        .setAttr("line-color", new LineColor(isTunnel, false, lineColorLevels))
        .setAttr("line-width", new LineWidth(false, isLink, lineWidthLevels))
        .setAttr("line-width-z20", 5 * (lineWidthLevels[14] - (isLink ? 1.5 : 0)));
    }

    if (sourceFeature.canBeLine() && sourceFeature.hasTag("highway", "secondary", "secondary_link")) {

      int categoryIndex = Arrays.asList(highwayCategories).indexOf("secondary");
      boolean isLink = sourceFeature.getTag("highway").toString().endsWith("_link");
      boolean isTunnel = isTunnel(sourceFeature);
      boolean isBridge = isBridge(sourceFeature);
      int minZoom = isLink ? linkMinZoom : 9;
      int maxZoom = globalMaxZoom;
      Integer layer = sourceFeature.hasTag("layer") ? Integer.parseInt(sourceFeature.getTag("layer").toString()) : null;

      double[] lineWidthLevels = {
        0.0, // z0
        0.0, // z1
        0.0, // z2
        0.0, // z3
        0.0, // z4
        0.0, // z5
        0.0, // z6
        0.0, // z7
        0.0, // z8
        1.0, // z9
        1.0, // z10
        2.0, // z11
        2.5, // z12
        3.0, // z13
        3.5, // z14
      };

      String[] casingLineColorLevels = {
        "", // z0
        "", // z1
        "", // z2
        "", // z3
        "", // z4
        "", // z5
        "", // z6
        "", // z7
        "", // z8
        "#9b59b6", // z9
        "#9b59b6", // z10
        "#9b59b6", // z11
        "#9b59b6", // z12
        "#9b59b6", // z13
        "#9b59b6", // z14
      };

      String[] lineColorLevels = {
        "", // z0
        "", // z1
        "", // z2
        "", // z3
        "", // z4
        "", // z5
        "", // z6
        "", // z7
        "", // z8
        "white", // z9
        "white", // z10
        "white", // z11
        "white", // z12
        "white", // z13
        "white", // z14
      };

      features.line("highway")
        .setMinPixelSize(0)
        .setMinZoom(minZoom)
        .setMaxZoom(maxZoom)
        .setAttr("line-sort-key", new LineSortKey(categoryIndex, isLink, isBridge, isTunnel, layer, true))
        .setAttr("line-color", new LineColor(isTunnel, true, casingLineColorLevels))
        .setAttr("line-width", new LineWidth(true, isLink, lineWidthLevels))
        .setAttr("line-width-z20", 5 * (lineWidthLevels[14] - (isLink ? 1.5 : 0)) + 2);
      
      features.line("highway")
        .setMinPixelSize(0)
        .setMinZoom(minZoom)
        .setMaxZoom(maxZoom)
        .setAttr("line-sort-key", new LineSortKey(categoryIndex, isLink, isBridge, isTunnel, layer, false))
        .setAttr("line-color", new LineColor(isTunnel, false, lineColorLevels))
        .setAttr("line-width", new LineWidth(false, isLink, lineWidthLevels))
        .setAttr("line-width-z20", 5 * (lineWidthLevels[14] - (isLink ? 1.5 : 0)));
    }

    if (sourceFeature.canBeLine() && sourceFeature.hasTag("highway", "primary", "primary_link")) {

      int categoryIndex = Arrays.asList(highwayCategories).indexOf("primary");
      boolean isLink = sourceFeature.getTag("highway").toString().endsWith("_link");
      boolean isTunnel = isTunnel(sourceFeature);
      boolean isBridge = isBridge(sourceFeature);
      int minZoom = isLink ? linkMinZoom : 8;
      int maxZoom = globalMaxZoom;
      Integer layer = sourceFeature.hasTag("layer") ? Integer.parseInt(sourceFeature.getTag("layer").toString()) : null;

      double[] lineWidthLevels = {
        0.0, // z0
        0.0, // z1
        0.0, // z2
        0.0, // z3
        0.0, // z4
        0.0, // z5
        0.0, // z6
        0.0, // z7
        1.0, // z8
        1.0, // z9
        1.0, // z10
        2.0, // z11
        2.5, // z12
        3.0, // z13
        3.5, // z14
      };

      String[] casingLineColorLevels = {
        "", // z0
        "", // z1
        "", // z2
        "", // z3
        "", // z4
        "", // z5
        "", // z6
        "", // z7
        "#3498db", // z8
        "#3498db", // z9
        "#3498db", // z10
        "#3498db", // z11
        "#3498db", // z12
        "#3498db", // z13
        "#3498db", // z14
      };

      String[] lineColorLevels = {
        "", // z0
        "", // z1
        "", // z2
        "", // z3
        "", // z4
        "", // z5
        "", // z6
        "", // z7
        "white", // z8
        "white", // z9
        "white", // z10
        "white", // z11
        "white", // z12
        "white", // z13
        "white", // z14
      };

      features.line("highway")
        .setMinPixelSize(0)
        .setMinZoom(minZoom)
        .setMaxZoom(maxZoom)
        .setAttr("line-sort-key", new LineSortKey(categoryIndex, isLink, isBridge, isTunnel, layer, true))
        .setAttr("line-color", new LineColor(isTunnel, true, casingLineColorLevels))
        .setAttr("line-width", new LineWidth(true, isLink, lineWidthLevels))
        .setAttr("line-width-z20", 5 * (lineWidthLevels[14] - (isLink ? 1.5 : 0)) + 2);
      
      features.line("highway")
        .setMinPixelSize(0)
        .setMinZoom(minZoom)
        .setMaxZoom(maxZoom)
        .setAttr("line-sort-key", new LineSortKey(categoryIndex, isLink, isBridge, isTunnel, layer, false))
        .setAttr("line-color", new LineColor(isTunnel, false, lineColorLevels))
        .setAttr("line-width", new LineWidth(false, isLink, lineWidthLevels))
        .setAttr("line-width-z20", 5 * (lineWidthLevels[14] - (isLink ? 1.5 : 0)));
    }

    if (sourceFeature.canBeLine() && sourceFeature.hasTag("highway", "trunk", "trunk_link")) {

      int categoryIndex = Arrays.asList(highwayCategories).indexOf("trunk");
      boolean isLink = sourceFeature.getTag("highway").toString().endsWith("_link");
      boolean isTunnel = isTunnel(sourceFeature);
      boolean isBridge = isBridge(sourceFeature);
      int minZoom = isLink ? linkMinZoom : 7;
      int maxZoom = globalMaxZoom;
      Integer layer = sourceFeature.hasTag("layer") ? Integer.parseInt(sourceFeature.getTag("layer").toString()) : null;

      double[] lineWidthLevels = {
        0.0, // z0
        0.0, // z1
        0.0, // z2
        0.0, // z3
        0.0, // z4
        0.0, // z5
        0.0, // z6
        1.0, // z7
        1.5, // z8
        2.0, // z9
        2.5, // z10
        3.0, // z11
        3.5, // z12
        4.0, // z13
        4.5, // z14
      };

      String[] casingLineColorLevels = {
        "", // z0
        "", // z1
        "", // z2
        "", // z3
        "", // z4
        "", // z5
        "", // z6
        "#2ecc71", // z7
        "#2ecc71", // z8
        "#2ecc71", // z9
        "#2ecc71", // z10
        "#2ecc71", // z11
        "#2ecc71", // z12
        "#2ecc71", // z13
        "#2ecc71", // z14
      };

      String[] lineColorLevels = {
        "", // z0
        "", // z1
        "", // z2
        "", // z3
        "", // z4
        "", // z5
        "", // z6
        "white", // z7
        "white", // z8
        "white", // z9
        "white", // z10
        "white", // z11
        "white", // z12
        "white", // z13
        "white", // z14
      };

      features.line("highway")
        .setMinPixelSize(0)
        .setMinZoom(minZoom)
        .setMaxZoom(maxZoom)
        .setAttr("line-sort-key", new LineSortKey(categoryIndex, isLink, isBridge, isTunnel, layer, true))
        .setAttr("line-color", new LineColor(isTunnel, true, casingLineColorLevels))
        .setAttr("line-width", new LineWidth(true, isLink, lineWidthLevels))
        .setAttr("line-width-z20", 5 * (lineWidthLevels[14] - (isLink ? 1.5 : 0)) + 2);
      
      features.line("highway")
        .setMinPixelSize(0)
        .setMinZoom(minZoom)
        .setMaxZoom(maxZoom)
        .setAttr("line-sort-key", new LineSortKey(categoryIndex, isLink, isBridge, isTunnel, layer, false))
        .setAttr("line-color", new LineColor(isTunnel, false, lineColorLevels))
        .setAttr("line-width", new LineWidth(false, isLink, lineWidthLevels))
        .setAttr("line-width-z20", 5 * (lineWidthLevels[14] - (isLink ? 1.5 : 0)));
    }

    if (sourceFeature.canBeLine() && sourceFeature.hasTag("highway", "motorway", "motorway_link")) {

      int categoryIndex = Arrays.asList(highwayCategories).indexOf("motorway");
      boolean isLink = sourceFeature.getTag("highway").toString().endsWith("_link");
      boolean isTunnel = isTunnel(sourceFeature);
      boolean isBridge = isBridge(sourceFeature);
      int minZoom = isLink ? linkMinZoom : 6;
      int maxZoom = globalMaxZoom;
      Integer layer = sourceFeature.hasTag("layer") ? Integer.parseInt(sourceFeature.getTag("layer").toString()) : null;

      double[] lineWidthLevels = {
        0.0, // z0
        0.0, // z1
        0.0, // z2
        0.0, // z3
        0.0, // z4
        0.0, // z5
        1.0, // z6
        1.5, // z7
        2.0, // z8
        2.5, // z9
        3.0, // z10
        3.5, // z11
        4.0, // z12
        4.5, // z13
        5.0, // z14
      };

      String[] casingLineColorLevels = {
        "", // z0
        "", // z1
        "", // z2
        "", // z3
        "", // z4
        "", // z5
        "#fab724", // z6
        "#fab724", // z7
        "#fab724", // z8
        "#fab724", // z9
        "#fab724", // z10
        "#fab724", // z11
        "#fab724", // z12
        "#fab724", // z13
        "#fab724", // z14
      };

      String[] lineColorLevels = {
        "", // z0
        "", // z1
        "", // z2
        "", // z3
        "", // z4
        "", // z5
        "#feefc3", // z6
        "#feefc3", // z7
        "#feefc3", // z8
        "#feefc3", // z9
        "#feefc3", // z10
        "#feefc3", // z11
        "#feefc3", // z12
        "#feefc3", // z13
        "#feefc3", // z14
      };

      features.line("highway")
        .setMinPixelSize(0)
        .setMinZoom(minZoom)
        .setMaxZoom(maxZoom)
        .setAttr("line-sort-key", new LineSortKey(categoryIndex, isLink, isBridge, isTunnel, layer, true))
        .setAttr("line-color", new LineColor(isTunnel, true, casingLineColorLevels))
        .setAttr("line-width", new LineWidth(true, isLink, lineWidthLevels))
        .setAttr("line-width-z20", 5 * (lineWidthLevels[14] - (isLink ? 1.5 : 0)) + 2);
      
      features.line("highway")
        .setMinPixelSize(0)
        .setMinZoom(minZoom)
        .setMaxZoom(maxZoom)
        .setAttr("line-sort-key", new LineSortKey(categoryIndex, isLink, isBridge, isTunnel, layer, false))
        .setAttr("line-color", new LineColor(isTunnel, false, lineColorLevels))
        .setAttr("line-width", new LineWidth(false, isLink, lineWidthLevels))
        .setAttr("line-width-z20", 5 * (lineWidthLevels[14] - (isLink ? 1.5 : 0)));
    }

  }

  @Override
  public List<VectorTile.Feature> postProcessLayerFeatures(String layer, int zoom,
    List<VectorTile.Feature> items) {

    if ("highway".equals(layer)) {
      return FeatureMerge.mergeLineStrings(items,
        0.5,
        zoom < 13 ? 0.5 : 0.1,
        4
      );
    }

    return null;
  }

  @Override
  public String name() {
    return "SingleHighwayLayer";
  }

  @Override
  public String description() {
    return "A map with a single highway layer";
  }

  @Override
  public String attribution() {
    return "<a href=\"https://www.openstreetmap.org/copyright\" target=\"_blank\">&copy; OpenStreetMap contributors</a>";
  }

  public static void main(String[] args) throws Exception {
    run(Arguments.fromArgsOrConfigFile(args));
  }

  static void run(Arguments args) throws Exception {
    String area = args.getString("area", "geofabrik area to download", "monaco");
    Planetiler.create(args)
      .setProfile(new SingleHighwayLayer())
      .addOsmSource("osm", Path.of("data", "sources", area + ".osm.pbf"), "planet".equals(area) ? "aws:latest" : ("geofabrik:" + area))
      .overwriteOutput("mbtiles", Path.of("data", "single-highway-layer.mbtiles"))
      .run();
  }
}