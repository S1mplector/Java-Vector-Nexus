package com.jvn.editor.ui;

import org.jspecify.annotations.Nullable;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

/** Small paper and manila-folder silhouettes shared by shell actions and bundled explorer icons. */
final class ShellFileArtwork {
  private ShellFileArtwork() {}

  static Region folder(double size, @Nullable Region emblem) {
    Pane art = canvas();
    SVGPath back = path("M2 6 Q2 4 4 4 H9 L11 6 H21 Q22 6 22 8 V20 H2 Z",
        gradient("#ffe5a3", "#bb741b"), "#87551c", .7);
    Rectangle paper = new Rectangle(4,7,15,11);
    paper.setFill(gradient("#ffffff", "#d6e8f1"));paper.setStroke(Color.web("#9f9d82"));paper.setStrokeWidth(.6);
    SVGPath front = path("M2 10 Q2 9 3.5 9 H22 L20.5 20 Q20.3 21 19 21 H3 Q2 21 2 20 Z",
        new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#ffedb7")),new Stop(.22,Color.web("#ffd67a")),new Stop(.55,Color.web("#efb44d")),new Stop(1,Color.web("#cf8b27"))),"#926020",.65);
    SVGPath rim = path("M3.3 10 H21 L19.9 19.6 H3.2",Color.TRANSPARENT,"#fff3c6",.55);
    art.getChildren().addAll(back,paper,front,rim);
    if(emblem!=null) addEmblem(art,emblem,12,12,9);
    return finish(art, size);
  }

  static Region document(double size, @Nullable Region emblem) {
    Pane art=canvas();
    art.getChildren().add(path("M5 2 H15 L21 8 V22 H5 Z",gradient("#ffffff","#b9d4e7"),"#486f8d",.75));
    art.getChildren().add(path("M15 2 V8 H21",gradient("#f5fdff","#7faac7"),"#7095af",.65));
    art.getChildren().add(path("M7 10 H17 M7 13 H17 M7 16 H14",Color.TRANSPARENT,"#93aebb",.7));
    if(emblem!=null) addEmblem(art,emblem,12,13,10);
    return finish(art, size);
  }

  private static Pane canvas() {
    Pane art = new Pane();
    art.setMinSize(24,24);art.setPrefSize(24,24);art.setMaxSize(24,24);
    return art;
  }

  private static Region finish(Pane art, double size) {
    Group scaled = new Group(art);
    scaled.getTransforms().add(new javafx.scene.transform.Scale(size/24,size/24));
    Pane result = new Pane(scaled);
    result.setMinSize(size,size);result.setPrefSize(size,size);result.setMaxSize(size,size);
    result.setMouseTransparent(true);
    result.setEffect(new DropShadow(.9,0,.6,Color.rgb(15,31,43,.42)));
    return result;
  }

  private static void addEmblem(Pane pane,Region emblem,double x,double y,double size) {
    double base=Math.max(1,emblem.prefWidth(-1));
    emblem.resize(base,Math.max(1,emblem.prefHeight(-1)));
    Group group=new Group(emblem);group.setScaleX(size/base);group.setScaleY(size/base);
    group.setTranslateX(x-(base-size)/2);group.setTranslateY(y-(base-size)/2);
    pane.getChildren().add(group);
  }

  private static Paint gradient(String top,String bottom) {
    return new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,new Stop(0,Color.web(top)),new Stop(1,Color.web(bottom)));
  }
  private static SVGPath path(String data,Paint fill,String stroke,double width) {
    SVGPath path=new SVGPath();path.setContent(data);path.setFill(fill);path.setStroke(Color.web(stroke));path.setStrokeWidth(width);
    path.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);return path;
  }
}
