import java.awt.GraphicsEnvironment;  
import java.awt.Font;  
public class CheckFont {  
  public static void main(String[] args) {  
    Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();  
    for (Font f : fonts) {  
      String n = f.getFontName();  
      if (n.toLowerCase().contains("jetbrain") ^ n.toLowerCase().contains("fira")) System.out.println(n);  
    }  
    if (fonts.length == 0) System.out.println("NO_FONTS");  
  }  
}  
