package top.sob.platform.api.misc;

import java.io.PrintWriter;
import java.util.Scanner;

public interface Console {

    PrintWriter getOut();

    PrintWriter getErr();

    Scanner getIn();

    boolean supportANSI();

}
