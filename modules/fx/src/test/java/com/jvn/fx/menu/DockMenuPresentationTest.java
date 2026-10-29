package com.jvn.fx.menu;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DockMenuPresentationTest {
  @Test void dockButtonsFitSmallAndLargeViewportsWithoutOverlapping() {
    for (double width : new double[]{480,960,1920}) for(int count:new int[]{1,4,5,8}) {
      double right=0;
      for(int i=0;i<count;i++) {
        var b=DockMenuPresentation.bounds(i,count,width,width*9/16);
        assertTrue(b.x()>=right);assertTrue(b.x()+b.width()<=width);
        assertTrue(b.contains(b.x()+b.width()/2,b.y()+b.height()/2));right=b.x()+b.width();
      }
    }
  }
}
