package edu.mit.wi.haploview;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
//import org.freehep.util.export.ExportDialog;
//import org.freehep.graphics2d.VectorGraphics;
//import org.freehep.graphicsio.ps.PSGraphics2D;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import java.util.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.*;
import javax.swing.*;
import javax.swing.border.CompoundBorder;


public class DPrimeDisplay extends JComponent
        implements MouseListener, MouseMotionListener, Constants {
    static final long serialVersionUID = -1547877018850397541L;
    private static final int H_BORDER = 30;
    private static final int V_BORDER = 15;
    private static final int TEXT_GAP = 3;
    private static final int GBROWSE_MARGIN = 25;
    private static final int MAX_GBROWSE_WIDTH = 30000;
    private static final int LAST_SELECTION_LEFT = 7;
    private static final int LAST_SELECTION_TOP  = 18;


    private static final int BOX_SIZES[] = {50, 24, 12};
    private static final int BOX_RADII[] = {24, 11, 6};
    private static final int TICK_HEIGHT = 8;
    private static final int TICK_BOTTOM = 50;

    private static final int TRACK_BUMPER = 3;
    private static final int TRACK_PALETTE = 50;
    private static final int TRACK_HEIGHT = TRACK_PALETTE + TRACK_BUMPER*2;
    private static final int TRACK_GAP = 5;

    private int widestMarkerName = 80; //default size
    private int blockDispHeight = 0, infoHeight = 0;
    private int boxSize = BOX_SIZES[0];
    private int boxRadius = BOX_RADII[0];
    private int lowX, highX, lowY, highY;
    private int left = H_BORDER;
    private int top = V_BORDER;
    private int clickXShift, clickYShift;
    private Vector displayStrings;
    private final int popupHorizMargin = 12;

    private final Color BG_GREY = new Color(212,208,200);

    private BufferedImage gBrowseImage = null;

    BasicStroke thickerStroke = new BasicStroke(1);
    BasicStroke thinnerStroke = new BasicStroke(0.35f);
    BasicStroke fatStroke = new BasicStroke(2.5f);
    float dash1[] = {5.0f};
    BasicStroke dashedFatStroke = new BasicStroke(2.5f,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            5.0f, dash1, 0.0f);
    BasicStroke dashedThinStroke = new BasicStroke(0.35f,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            5.0f, dash1, 0.0f);
    private Font boxFont = new Font("SansSerif", Font.PLAIN, 12);
    private Font markerNumFont = new Font("SansSerif", Font.BOLD, 12);
    private Font markerNameFont = new Font("Default", Font.PLAIN, 12);
    private Font boldMarkerNameFont = new Font("Default", Font.BOLD, 12);
    private Font popupFont = new Font("Monospaced", Font.PLAIN, 12);

    private int printWhat = D_PRIME;
    private boolean printMarkerNames = true;
    private boolean forExport = false;
    private int exportStart, exportStop;
    private boolean showWM = false;
    private int zoomLevel = 0;
    private boolean noImage = true;

    private Rectangle wmInteriorRect = new Rectangle();
    private Rectangle wmResizeCorner = new Rectangle(0,0,-1,-1);
    private Rectangle resizeWMRect = null;
    private Point popupDrawPoint = null;
    private BufferedImage worldmap;
    private HaploData theData;
    private HaploView theHV;
    private Dimension chartSize=null;
    private int wmMaxWidth=0;
    private Rectangle blockRect = null;
    private int blockStartX = 0;
    private double[] alignedPositions;
    private String currentSelection;
    private String lastSelection = new String("");


    DPrimeDisplay(HaploView h){
        //called when in gui mode
        theData=h.theData;
        theHV = h;
        this.computePreferredSize(theHV.getGraphics());
        this.colorDPrime();
        this.setDoubleBuffered(true);
        addMouseListener(this);
        addMouseMotionListener(this);
        this.setAutoscrolls(true);
    }

    DPrimeDisplay(HaploData hd){
        //called when in cmd line mode, used to dump pngs
        theData = hd;
        this.computePreferredSize();
        this.colorDPrime();
    }

    public void colorDPrime(){
        int scheme = Options.getLDColorScheme();
        DPrimeTable dPrime = theData.dpTable;
        noImage = true;

        if (scheme == STD_SCHEME){
            // set coloring based on LOD and D'
            for (int i = 0; i < Chromosome.getSize()-1; i++){
                for (int j = i+1; j < dPrime.getLength(i)+i; j++){
                    PairwiseLinkage thisPair = dPrime.getLDStats(i,j);
                    if (thisPair == null){
                        continue;
                    }

                    double d = thisPair.getDPrime();
                    double l = thisPair.getLOD();
                    Color boxColor;
                    if (l > 2) {
                        if (d < 0.5) {
                            //high LOD, low D'
                            boxColor = new Color(255, 224, 224);
                        } else {
                            //high LOD, high D' shades of red
                            double blgr = (255-32)*2*(1-d);
                            boxColor = new Color(255, (int) blgr, (int) blgr);
                        }
                    } else if (d > 0.99) {
                        //high D', low LOD blueish color
                        boxColor = new Color(192, 192, 240);
                    } else {
                        //no LD
                        boxColor = Color.white;
                    }
                    thisPair.setColor(boxColor);
                }
            }
        }else if (scheme == GAB_SCHEME){
            for (int x = 0; x < Chromosome.getSize()-1; x++){
                for (int y = x+1; y < Chromosome.getSize(); y++){
                    PairwiseLinkage thisPair = dPrime.getLDStats(x,y);
                    if (thisPair == null){
                        continue;
                    }
                    //get the right bits
                    double lowCI = thisPair.getConfidenceLow();
                    double highCI = thisPair.getConfidenceHigh();

                    //color in squares
                    if (lowCI >= FindBlocks.cutLowCI && highCI >= FindBlocks.cutHighCI) {
                        thisPair.setColor(Color.darkGray);  //strong LD
                    }else if (highCI >= FindBlocks.recHighCI) {
                        thisPair.setColor(Color.lightGray); //uninformative
                    } else {
                        thisPair.setColor(Color.white); //recomb
                    }
                }
            }
        }else if (scheme == GAM_SCHEME){
            for (int x = 0; x < Chromosome.getSize()-1; x++){
                for (int y = x+1; y < Chromosome.getSize(); y++){
                    PairwiseLinkage thisPair = dPrime.getLDStats(x,y);
                    if (thisPair == null) {
                        continue;
                    }

                    double[] freqs = thisPair.getFreqs();
                    int numGam = 0;
                    for (int i = 0; i < freqs.length; i++){
                        //add a little bump for EM probs which should be zero but are really like 10^-10
                        if (freqs[i] > FindBlocks.fourGameteCutoff + 1E-8) numGam++;
                    }

                    //color in squares
                    if(numGam > 3){
                        thisPair.setColor(Color.white);
                    }else{
                        thisPair.setColor(Color.darkGray);
                    }
                }
            }
        }else if (scheme == WMF_SCHEME){
            // set coloring based on LOD and D', but without (arbitrary) cutoffs to introduce
            // "color damage" (Tufte)

            // first get the maximum LOD score so we can scale relative to that.

            double max_l = 0.0;

            for (int i = 0; i < Chromosome.getSize(); i++){
                for (int j = i+1; j < i + dPrime.getLength(i); j++){
                    PairwiseLinkage thisPair = dPrime.getLDStats(i,j);
                    if (thisPair == null){
                        continue;
                    }

                    if (thisPair.getLOD() > max_l) max_l = thisPair.getLOD();
                }
            }

            // cap the max LOD score
            if (max_l > 5.0) max_l = 5.0;

            for (int i = 0; i < Chromosome.getSize(); i++){
                for (int j = i+1; j < i + dPrime.getLength(i); j++){
                    PairwiseLinkage thisPair = dPrime.getLDStats(i,j);
                    if (thisPair == null){
                        continue;
                    }

                    double d = thisPair.getDPrime();
                    double l = thisPair.getLOD();
                    Color boxColor = null;

                    double lod_scale = l / max_l;

                    // if greater than the cap, call it the cap
                    if (lod_scale > 1.0) lod_scale = 1.0;

                    // there can be negative LOD scores, apparently
                    if (lod_scale < 0.0) lod_scale = 0.0;

                    // also, scale the D' so anything under .2 is white.
                    d = (1.0 / 0.8) * (d - 0.2);
                    if (d < 0.0) d = 0.0;

                    // if there is low(er) D' but big LOD score, this should be in a gray scale
                    // scaled to the D' value
                    if (lod_scale > d) { lod_scale = d; }

                    int r, g, b;

                    // r = (int)(200.0 * d + 55.0 * lod_scale);
                    // g = (int)(255.0 * d - 255.0 * lod_scale);
                    // b = (int)(255.0 * d - 255.0 * lod_scale);

                    double ap, cp, dp, ep, jp, kp;

                    ap = 0.0;
                    cp = -255.0;
                    dp = -55.0;
                    ep = -200.0;
                    jp = 255.0;
                    kp = 255.0;

                    r =     (int)(ap * d + cp * lod_scale + jp);
                    g = b = (int)(dp * d + ep * lod_scale + kp);

                    if (r < 0) r = 0;
                    if (g < 0) g = 0;
                    if (b < 0) b = 0;

                    boxColor = new Color(r, g, b);

                    thisPair.setColor(boxColor);
                }
            }
        }else if (scheme == RSQ_SCHEME){
            // set coloring based on R-squared values

            for (int i = 0; i < Chromosome.getSize(); i++){
                for (int j = i+1; j < i + dPrime.getLength(i); j++){
                    PairwiseLinkage thisPair = dPrime.getLDStats(i,j);
                    if (thisPair == null){
                        continue;
                    }

                    double rsq = thisPair.getRSquared();
                    Color boxColor = null;

                    int r, g, b;

                    r = g = b = (int)(255.0 * (1.0 - rsq));

                    boxColor = new Color(r, g, b);

                    thisPair.setColor(boxColor);
                }
            }
        }else if (scheme == GOLD_SCHEME){
            for (int i = 0; i < Chromosome.getSize(); i++){
                for (int j = i+1; j < i + dPrime.getLength(i); j++){
                    PairwiseLinkage thisPair = dPrime.getLDStats(i,j);
                    if (thisPair == null){
                        continue;
                    }
                    double dprime = thisPair.getDPrime();
                    int r,g,b;
                    if (dprime < 0.2){
                        r = 0;
                        g = 0;
                        b = 127 + (int)((dprime/0.2)*127);
                    }else if(dprime < 0.4){
                        r = 0;
                        g = (int)(((dprime-0.2)/0.2)*255);
                        b = 255;
                    }else if (dprime < 0.6){
                        r = 0;
                        g = 127 + (int)(((dprime-0.4)/0.2)*127);
                        b = 0;
                    }else if (dprime < 0.8){
                        r = (int)(((dprime-0.6)/0.2)*255);
                        g = 255;
                        b = 0;
                    }else{
                        r = 255;
                        g = (int)(((1-dprime)/0.2)*255);
                        b = 0;
                    }
                    thisPair.setColor(new Color(r,g,b));
                }
            }

        }
        repaint();
    }

    public BufferedImage export(int start, int stop, boolean compress) throws HaploViewException {
        forExport = true;

        exportStart = -1;
        if (start < 0){
            start = 0;
        }
        while (true){
            //if the marker we want has been filtered walk up until we find a valid one
            exportStart = Chromosome.filterIndex[start];
            if (exportStart == -1){
                start++;
                if (start >= Chromosome.getUnfilteredSize()){
                    forExport = false;
                    throw new HaploViewException("Invalid marker range for export.");
                }
            }else{
                break;
            }
        }

        exportStop = -1;
        if (stop > Chromosome.getUnfilteredSize()){
            stop = Chromosome.getUnfilteredSize();
        }
        while (true){
            //if the marker we want has been filtered walk down until we find a valid one
            exportStop = Chromosome.filterIndex[stop-1];
            if (exportStop == -1){
                stop--;
                if (stop < 0){
                    forExport = false;
                    throw new HaploViewException("Invalid marker range for export.");
                }
            }else{
                break;
            }
        }


        this.computePreferredSize();

        int startBS = boxSize;
        int startBR = boxRadius;
        int startPW = printWhat;
        boolean startMN = printMarkerNames;
        int startZL = zoomLevel;

        if (compress){
            zoomLevel = 2;
            printWhat = LD_NONE;
            printMarkerNames = false;

            if (boxSize > (1200/(stop - start))){
                boxSize = 1200/(stop - start);

                if (boxSize < 2){
                    boxSize = 2;
                }
                //to make picture not look dumb we need to avoid odd numbers for really teeny boxes
                if (boxSize < 10){
                    if (boxSize%2 != 0){
                        boxSize++;
                    }
                }
                boxRadius = boxSize/2;
            }
            this.computePreferredSize();
        }

        Dimension pref = getPreferredSize();
        if(pref.width > 10000 || pref.height > 10000) {
            throw new HaploViewException("Image too large. Try saving as compressed PNG.");
        }
        BufferedImage i = new BufferedImage(pref.width, pref.height,
                BufferedImage.TYPE_3BYTE_BGR);
        paintComponent(i.getGraphics());

/*        try{
            VectorGraphics g = new PSGraphics2D(new File("foo.ps"),new Dimension(pref.width,pref.height));
            g.startExport();
            print(g);
            g.endExport();
        }catch (Exception e){

        }
        //todo: rewrite this so freehep correctly sizes object when exporting.
        //org.freehep.util.export.ExportDialog ed = new ExportDialog();
        //ed.showExportDialog(this,"zoo",this,"zoo");
        */

        boxSize = startBS;
        boxRadius = startBR;
        zoomLevel = startZL;
        printMarkerNames = startMN;
        printWhat = startPW;
        forExport = false;
        this.computePreferredSize();
        return i;
    }

    public SVGGraphics2D exportSVG(int start, int stop) throws HaploViewException {
       forExport = true;

        exportStart = -1;
        if (start < 0){
            start = 0;
        }
        while (true){
            //if the marker we want has been filtered walk up until we find a valid one
            exportStart = Chromosome.filterIndex[start];
            if (exportStart == -1){
                start++;
                if (start >= Chromosome.getUnfilteredSize()){
                    forExport = false;
                    throw new HaploViewException("Invalid marker range for export.");
                }
            }else{
                break;
            }
        }

        exportStop = -1;
        if (stop > Chromosome.getUnfilteredSize()){
            stop = Chromosome.getUnfilteredSize();
        }
        while (true){
            //if the marker we want has been filtered walk down until we find a valid one
            exportStop = Chromosome.filterIndex[stop-1];
            if (exportStop == -1){
                stop--;
                if (stop < 0){
                    forExport = false;
                    throw new HaploViewException("Invalid marker range for export.");
                }
            }else{
                break;
            }
        }


        this.computePreferredSize();

        int startBS = boxSize;
        int startBR = boxRadius;
        int startPW = printWhat;
        boolean startMN = printMarkerNames;
        int startZL = zoomLevel;

        DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
        Document document = domImpl.createDocument(null, "svg", null);
        SVGGraphics2D svgGenerator = new SVGGraphics2D(document);
        svgGenerator.getGeneratorContext().setPrecision(6); //TODO: Look at changing precision
        paintComponent(svgGenerator);

        boxSize = startBS;
        boxRadius = startBR;
        zoomLevel = startZL;
        printMarkerNames = startMN;
        printWhat = startPW;
        forExport = false;
        this.computePreferredSize();

        return svgGenerator;
    }

    public void zoom(int type){
        int diff = type - zoomLevel;

        zoomLevel = type;

        if (zoomLevel == 0){
            printMarkerNames = true;
        } else{
            printMarkerNames = false;
        }

        int x=0, y=0;
        int oldX = getVisibleRect().x;
        int oldY = getVisibleRect().y;
        int oldWidth = getVisibleRect().width;
        int oldHeight = getVisibleRect().height;

        if (diff > 0){
            //we're zooming out
            x = oldX /(2*diff) - oldWidth/4*diff;
            y = oldY /(2*diff) - oldHeight/4*diff;
        } else if (diff < 0 ) {
            //we're zooming in
            diff = -diff;
            x = oldX*2*diff + oldWidth/2*diff;
            y = oldY*2*diff + oldHeight/2*diff;

            //for cases when zoomed out view doesn't take up whole screen we don't wanna end
            //up zooming in to some random place, but rather the upper left corner.
            if (oldX == 0){
                x = 0;
            }
            if (oldY == 0){
                y = 0;
            }
        }else{
            //we didn't change the zoom so don't waste cycles
            return;
        }

        if (x < 0){
            x = 0;
        }
        if (y < 0){
            y = 0;
        }
        boxSize = BOX_SIZES[zoomLevel];
        boxRadius = BOX_RADII[zoomLevel];
        this.computePreferredSize();
        //System.out.println(oldX + " " + x + " " + oldY + " " + y);
        ((JViewport)getParent()).setViewPosition(new Point(x,y));
    }

    public void paintComponent(Graphics g){
        DPrimeTable dPrimeTable = theData.dpTable;
        if (Chromosome.getSize() < 2){
            //if there zero or only one valid marker
            return;
        }
        Vector blocks = theData.blocks;
        Rectangle visRect = getVisibleRect();

        //deal with zooming
        if (chartSize.getWidth() > (3*visRect.width)){
            showWM = true;
        }else{
            showWM = false;
        }

        boolean printValues = true;
        if (zoomLevel != 0 || Options.getPrintWhat() == LD_NONE){
            printValues = false;
        }
        printWhat = Options.getPrintWhat();

        Graphics2D g2 = (Graphics2D) g;
        Dimension size = getSize();
        Dimension pref = getPreferredSize();
        g2.setColor(BG_GREY);

        //if it's a big dataset, resize properly, if it's small make sure to fill whole background
        if (size.height < pref.height){
            g2.fillRect(0,0,pref.width,pref.height);
            setSize(pref);
        }else{
            g2.fillRect(0,0,size.width, size.height);
        }
        g2.setColor(Color.black);


        //okay so this dumb if block is to prevent the ugly repainting
        //bug when loading markers after the data are already being displayed,
        //results in a little off-centering for small datasets, but not too bad.
        if (!forExport){
            if (!theData.infoKnown){
                g2.translate((size.width - pref.width) / 2,
                        (size.height - pref.height) / 2);
            } else {
                g2.translate((size.width - pref.width) / 2,
                        0);
            }
        }

        FontMetrics boxFontMetrics = g2.getFontMetrics(boxFont);

        int diamondX[] = new int[4];
        int diamondY[] = new int[4];
        Polygon diamond;


        double lineSpan = alignedPositions[alignedPositions.length-1] - alignedPositions[0];
        long minpos = Chromosome.getMarker(0).getPosition();
        long maxpos = Chromosome.getMarker(Chromosome.getSize()-1).getPosition();
        double spanpos = maxpos - minpos;


        //See http://www.hapmap.org/cgi-perl/gbrowse/gbrowse_img
        //for more info on GBrowse img.
        int imgHeight = 0;
        if (Options.isGBrowseShown() && Chromosome.getDataChrom() != null && !Chromosome.getDataChrom().equalsIgnoreCase("none") && gBrowseImage != null){
            g2.drawImage(gBrowseImage,H_BORDER-GBROWSE_MARGIN,V_BORDER,this);
            imgHeight = gBrowseImage.getHeight(this) + TRACK_GAP; // get height so we can shift everything down
        }
        left = H_BORDER;
        top = V_BORDER + imgHeight; // push the haplotype display down to make room for gbrowse image.


        if (forExport){
            left -= exportStart * boxSize;
        }

        FontMetrics boldMetrics;
        int ascent;

        g2.setFont(boldMarkerNameFont);
        boldMetrics = g2.getFontMetrics();
        ascent = boldMetrics.getAscent();

        //the following values are the bounds on the boxes we want to
        //display given that the current window is 'visRect'
        lowX = getBoundaryMarker(visRect.x-clickXShift-(visRect.y +visRect.height-clickYShift)) - 1;
        highX = getBoundaryMarker(visRect.x + visRect.width);
        lowY = getBoundaryMarker((visRect.x-clickXShift)+(visRect.y-clickYShift)) - 1;
        highY = getBoundaryMarker((visRect.x-clickXShift+visRect.width) + (visRect.y-clickYShift+visRect.height));
        if (lowX < 0) {
            lowX = 0;
        }
        if (highX > Chromosome.getSize()-1){
            highX = Chromosome.getSize()-1;
        }
        if (lowY < lowX+1){
            lowY = lowX+1;
        }
        if (highY > Chromosome.getSize()){
            highY = Chromosome.getSize();
        }

        if (forExport){
            lowX = exportStart;
            lowY = exportStart;
            highX = exportStop;
            highY = exportStop+1;
        }


        if (theData.trackExists){
            //draw the analysis track above where the marker positions will be marked

            JFreeChart jfc = ChartFactory.createXYLineChart(null,null,null,
                    theData.analysisTracks,
                    PlotOrientation.VERTICAL,false,false,false);

            //customise the analysis track
            XYPlot xyp = (XYPlot)jfc.getPlot();

            //no x axis, since it takes up too much space.
            xyp.getDomainAxis().setAxisLineVisible(false);
            xyp.getDomainAxis().setTickLabelsVisible(false);
            xyp.getDomainAxis().setTickMarksVisible(false);

            //x range must align with markers
            xyp.getDomainAxis().setRange(minpos,maxpos);

            //size of the axis and graph inset
            double axisWidth = xyp.getRangeAxis().
                    reserveSpace(g2,xyp,new Rectangle(0,TRACK_HEIGHT),RectangleEdge.LEFT,null).getLeft();
            RectangleInsets insets = xyp.getInsets();

            jfc.setBackgroundPaint(BG_GREY);
            BufferedImage bi = jfc.createBufferedImage(
                    (int)(lineSpan + axisWidth + insets.getLeft() + insets.getRight()),TRACK_HEIGHT);
            //hide the axis in the margin so everything lines up.
            g2.drawImage(bi,(int)(left - axisWidth - insets.getLeft()),top,this);
            top += TRACK_HEIGHT + TRACK_GAP;
        }

        if (theData.infoKnown) {
	    Color green = new Color(0, 170, 0);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            //// draw the marker locations

            g2.setStroke(thinnerStroke);
            g2.setColor(Color.white);
            g2.fill(new Rectangle2D.Double(left+1, top+1, lineSpan-1, TICK_HEIGHT-1));
            g2.setColor(Color.black);
            g2.draw(new Rectangle2D.Double(left, top, lineSpan, TICK_HEIGHT));

            for (int i = 0; i < Chromosome.getSize(); i++){
                double pos = (Chromosome.getMarker(i).getPosition() - minpos) / spanpos;

                double xx = left + lineSpan*pos;

                // if we're zoomed, use the line color to indicate whether there is extra data available
                // (since the marker names are not displayed when zoomed)

                if (Chromosome.getMarker(i).getExtra() != null) g2.setColor(green);

                //draw tick
                g2.setStroke(thickerStroke);
                g2.draw(new Line2D.Double(xx, top, xx, top + TICK_HEIGHT));

                if (Chromosome.getMarker(i).getExtra() != null) g2.setStroke(thickerStroke);
                else g2.setStroke(thinnerStroke);
                //draw connecting line
                g2.draw(new Line2D.Double(xx, top + TICK_HEIGHT,
                        left + alignedPositions[i], top+TICK_BOTTOM));

                if (theHV != null){
                    if (Chromosome.getMarker(i).getDisplayName().equals(theHV.getChosenMarker())){
                        float cornerx = (float)xx-10;
                        float cornery = top;
                        float xpoints[] = {cornerx,cornerx+20,cornerx+10};
                        float ypoints[] = {cornery,cornery,cornery-10};
                        GeneralPath triangle = new GeneralPath(GeneralPath.WIND_NON_ZERO,xpoints.length);
                        triangle.moveTo(xpoints[0],ypoints[0]);
                        for (int index = 1; index < xpoints.length; index++){
                            triangle.lineTo(xpoints[index],ypoints[index]);
                        }
                        triangle.closePath();
                        g2.fill(triangle);
                    }
                }

                g2.setColor(Color.black);
            }

            top += TICK_BOTTOM + TICK_HEIGHT;

            //// draw the marker names
            if (printMarkerNames){
                g2.setFont(boldMarkerNameFont);
                FontMetrics markerMetrics = g2.getFontMetrics();
                widestMarkerName = markerMetrics.stringWidth(Chromosome.getMarker(0).getDisplayName());
                for (int x = 1; x < Chromosome.getSize(); x++) {
                    int thiswide = markerMetrics.stringWidth(Chromosome.getMarker(x).getDisplayName());
                    if (thiswide > widestMarkerName) widestMarkerName = thiswide;
                }

                g2.translate(left, top + widestMarkerName);
                g2.rotate(-Math.PI / 2.0);
                for (int x = 0; x < Chromosome.getSize(); x++) {
                    if (theData.isInBlock[x]){
                        g2.setFont(boldMarkerNameFont);
                        markerMetrics = g2.getFontMetrics();
                    }else{
                        g2.setFont(markerNameFont);
                        markerMetrics = g2.getFontMetrics();
                    }
                    if (theHV != null){
                        if (Chromosome.getMarker(x).getDisplayName().equals(theHV.getChosenMarker())){
                            g2.setColor(Color.white);
                            g2.fillRect(TEXT_GAP,(int)alignedPositions[x] - ascent/2,
                                    markerMetrics.stringWidth(Chromosome.getMarker(x).getDisplayName()),ascent);
                            g2.setColor(green);
                            g2.drawRect(TEXT_GAP-1,(int)alignedPositions[x] - ascent/2 - 1,
                                    markerMetrics.stringWidth(Chromosome.getMarker(x).getDisplayName())+1,ascent+1);
                        }
                    }
                    if (Chromosome.getMarker(x).getExtra() != null) g2.setColor(green);


                    g2.drawString(Chromosome.getMarker(x).getDisplayName(),(float)TEXT_GAP, (float)alignedPositions[x] + ascent/3);
                    g2.setColor(Color.black);
                }

                g2.rotate(Math.PI / 2.0);
                g2.translate(-left, -(top + widestMarkerName));

                // move everybody down
                top += widestMarkerName + TEXT_GAP;
            }

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
        }

        top += blockDispHeight;

        //// draw the marker numbers
        if (printMarkerNames){
            g2.setFont(markerNumFont);
            FontMetrics numMetrics = g2.getFontMetrics();
            ascent = numMetrics.getAscent();

            for (int x = 0; x < Chromosome.getSize(); x++) {
                String mark = String.valueOf(Chromosome.realIndex[x] + 1);
                g2.drawString(mark,
                        (float)(left + alignedPositions[x] - numMetrics.stringWidth(mark)/2),
                        (float)(top + ascent));
            }

            top += boxRadius/2; // give a little space between numbers and boxes
        }

        //clickxshift and clickyshift are used later to translate from x,y coords
        //to the pair of markers comparison at those coords
        if (!(theData.infoKnown)){
            clickXShift = left + (size.width-pref.width)/2;
            clickYShift = top + (size.height - pref.height)/2;
        } else {
            clickXShift = left + (size.width-pref.width)/2;
            clickYShift = top;
        }


        // draw table column by column
        for (int x = lowX; x < highX; x++) {

            //always draw the fewest possible boxes
            if (lowY < x+1){
                lowY = x+1;
            }

            for (int y = lowY; y < highY; y++) {
                if (dPrimeTable.getLDStats(x,y) == null){
                    continue;
                }
                double d = dPrimeTable.getLDStats(x,y).getDPrime();
                double r = dPrimeTable.getLDStats(x,y).getRSquared();
                //double l = dPrimeTable.getLDStats(x,y).getLOD();
                Color boxColor = dPrimeTable.getLDStats(x,y).getColor();

                // draw markers above
                int xx = left + (int)((alignedPositions[x] + alignedPositions[y])/2);
                int yy = top + (int)((alignedPositions[y] - alignedPositions[x]) / 2);

                diamondX[0] = xx; diamondY[0] = yy - boxRadius;
                diamondX[1] = xx + boxRadius; diamondY[1] = yy;
                diamondX[2] = xx; diamondY[2] = yy + boxRadius;
                diamondX[3] = xx - boxRadius; diamondY[3] = yy;

                diamond = new Polygon(diamondX, diamondY, 4);
                g2.setColor(boxColor);
                g2.fillPolygon(diamond);

                if(printValues){
                    g2.setFont(boxFont);
                    ascent = boxFontMetrics.getAscent();
                    int val;
                    if (printWhat == D_PRIME){
                        val = (int) (d * 100);
                    }else if (printWhat == R_SQ){
                        val = (int) (r * 100);
                    }else{
                        val = 100;
                    }
                    if (boxColor.getGreen() < 175 && boxColor.getBlue() < 175 && boxColor.getRed() < 175){
                        g2.setColor(Color.white);
                    }else{
                        g2.setColor((val < 50) ? Color.gray : Color.black);
                    }
                    if (val != 100) {
                        String valu = String.valueOf(val);
                        int widf = boxFontMetrics.stringWidth(valu);
                        g.drawString(valu, xx - widf/2, yy + ascent/2);
                    }
                }
            }
        }

        //highlight blocks
        g2.setFont(markerNameFont);
        ascent = g2.getFontMetrics().getAscent();
        //g.setColor(new Color(153,255,153));
        g2.setColor(Color.black);
        //g.setColor(new Color(51,153,51));
        for (int i = 0; i < blocks.size(); i++){
            int[] theBlock = (int[])blocks.elementAt(i);
            int first = theBlock[0];
            int last = theBlock[theBlock.length-1];

            //big vee around whole thing
            g2.setStroke(fatStroke);
            g2.draw(new Line2D.Double(left + alignedPositions[first] - boxRadius,
                    top,
                    left + (alignedPositions[first] + alignedPositions[last])/2,
                    top + (alignedPositions[last] - alignedPositions[first])/2 + boxRadius));
            g2.draw(new Line2D.Double(left + (alignedPositions[first] + alignedPositions[last])/2,
                    top + (alignedPositions[last] - alignedPositions[first])/2 + boxRadius,
                    left + alignedPositions[last] + boxRadius,
                    top));

            for (int j = first; j < last; j++){
                g2.setStroke(fatStroke);
                if (theData.isInBlock[j]){
                    g2.draw(new Line2D.Double(left+alignedPositions[j]-boxSize/2,
                        top-blockDispHeight,
                        left+alignedPositions[j+1]-boxSize/2,
                        top-blockDispHeight));
                }else{
                    g2.draw(new Line2D.Double(left + alignedPositions[j] + boxSize/2,
                        top-blockDispHeight,
                        left+alignedPositions[j+1]-boxSize/2,
                        top-blockDispHeight));
                    g2.setStroke(dashedFatStroke);
                    g2.draw(new Line2D.Double(left+alignedPositions[j] - boxSize/2,
                        top-blockDispHeight,
                        left+alignedPositions[j] + boxSize/2,
                        top-blockDispHeight));
                }
            }
            //cap off the end of the block
            g2.setStroke(fatStroke);
            g2.draw(new Line2D.Double(left+alignedPositions[last]-boxSize/2,
                        top-blockDispHeight,
                        left+alignedPositions[last]+boxSize/2,
                        top-blockDispHeight));


            //lines to connect to block display
            g2.setStroke(fatStroke);
            g2.draw(new Line2D.Double(left + alignedPositions[first]-boxSize/2,
                    top-1,
                    left+alignedPositions[first]-boxSize/2,
                    top-blockDispHeight));
            g2.draw(new Line2D.Double(left+alignedPositions[last]+boxSize/2,
                    top-1,
                    left+alignedPositions[last]+boxSize/2,
                    top-blockDispHeight));
            if (printMarkerNames){
                String labelString = new String ("Block " + (i+1));
                if (theData.infoKnown){
                    long blockSize = Chromosome.getMarker(last).getPosition() -
                            Chromosome.getMarker(first).getPosition();
                    labelString += " (" + blockSize/1000 + " kb)";
                }
                g2.drawString(labelString,
                        (float)(left+alignedPositions[first]-boxSize/2+TEXT_GAP),
                        (float)(top-boxSize/3));
            }
        }
        g2.setStroke(thickerStroke);

        if (showWM && !forExport){
            //dataset is big enough to require worldmap
            if (wmMaxWidth == 0){
                wmMaxWidth = visRect.width/3;
            }
            double scalefactor;
            scalefactor = (double)(chartSize.width)/wmMaxWidth;
            double prefBoxSize = boxSize/(scalefactor*((double)wmMaxWidth/(double)(wmMaxWidth)));

            //stick WM_BD in the middle of the blank space at the top of the worldmap
            final int WM_BD_GAP = (int)(infoHeight/(scalefactor*2));
            final int WM_BD_HEIGHT = 2;
            CompoundBorder wmBorder = new CompoundBorder(BorderFactory.createRaisedBevelBorder(),
                    BorderFactory.createLoweredBevelBorder());

            if (noImage){
                //first time through draw a worldmap if dataset is big:
                worldmap = new BufferedImage((int)(chartSize.width/scalefactor)+wmBorder.getBorderInsets(this).left*2,
                        (int)(chartSize.height/scalefactor)+wmBorder.getBorderInsets(this).top*2,
                        BufferedImage.TYPE_3BYTE_BGR);

                Graphics gw = worldmap.getGraphics();
                Graphics2D gw2 = (Graphics2D)(gw);
                gw2.setColor(BG_GREY);
                gw2.fillRect(1,1,worldmap.getWidth()-1,worldmap.getHeight()-1);
                //make a pretty border
                gw2.setColor(Color.black);

                wmBorder.paintBorder(this,gw2,0,0,worldmap.getWidth(),worldmap.getHeight());
                wmInteriorRect = wmBorder.getInteriorRectangle(this,0,0,worldmap.getWidth(), worldmap.getHeight());

                float[] smallDiamondX = new float[4];
                float[] smallDiamondY = new float[4];
                GeneralPath gp;
                for (int x = 0; x < Chromosome.getSize()-1; x++){
                    for (int y = x+1; y < Chromosome.getSize(); y++){
                        if (dPrimeTable.getLDStats(x,y) == null){
                            continue;
                        }
                        double xx = ((alignedPositions[y] + alignedPositions[x])/(scalefactor*2)) +
                                wmBorder.getBorderInsets(this).left;
                        double yy = ((alignedPositions[y] - alignedPositions[x] + infoHeight*2)/(scalefactor*2)) +
                                wmBorder.getBorderInsets(this).top;

                        smallDiamondX[0] = (float)xx; smallDiamondY[0] = (float)(yy - prefBoxSize/2);
                        smallDiamondX[1] = (float)(xx + prefBoxSize/2); smallDiamondY[1] = (float)yy;
                        smallDiamondX[2] = (float)xx; smallDiamondY[2] = (float)(yy + prefBoxSize/2);
                        smallDiamondX[3] = (float)(xx - prefBoxSize/2); smallDiamondY[3] = (float)yy;

                        gp =  new GeneralPath(GeneralPath.WIND_EVEN_ODD,  smallDiamondX.length);
                        gp.moveTo(smallDiamondX[0],smallDiamondY[0]);
                        for (int i = 1; i < smallDiamondX.length; i++){
                            gp.lineTo(smallDiamondX[i], smallDiamondY[i]);
                        }
                        gp.closePath();

                        gw2.setColor(dPrimeTable.getLDStats(x,y).getColor());
                        gw2.fill(gp);

                    }
                }
                noImage = false;
            }

            //draw block display in worldmap
            Graphics gw = worldmap.getGraphics();
            Graphics2D gw2 = (Graphics2D)(gw);
            gw2.setColor(BG_GREY);
            gw2.fillRect(wmBorder.getBorderInsets(this).left,
                    wmBorder.getBorderInsets(this).top+WM_BD_GAP,
                    wmInteriorRect.width,
                    WM_BD_HEIGHT);
            gw2.setColor(Color.black);
            boolean even = true;
            for (int i = 0; i < blocks.size(); i++){
                int first = ((int[])blocks.elementAt(i))[0];
                int last = ((int[])blocks.elementAt(i))[((int[])blocks.elementAt(i)).length-1];
                int voffset;
                if (even){
                    voffset = 0;
                }else{
                    voffset = WM_BD_HEIGHT/2;
                }
                gw2.fillRect(wmBorder.getBorderInsets(this).left - (int)prefBoxSize/2 + (int)(alignedPositions[first]/scalefactor),
                        wmBorder.getBorderInsets(this).top+voffset+WM_BD_GAP,
                        (int)(prefBoxSize + (alignedPositions[last] - alignedPositions[first])/scalefactor),
                        WM_BD_HEIGHT/2);
                even = !even;
            }

            wmResizeCorner = new Rectangle(visRect.x + worldmap.getWidth() - (worldmap.getWidth()-wmInteriorRect.width)/2,
                    visRect.y + visRect.height - worldmap.getHeight(),
                    (worldmap.getWidth()-wmInteriorRect.width)/2,
                    (worldmap.getHeight() -wmInteriorRect.height)/2);

            g2.drawImage(worldmap,visRect.x,
                    visRect.y + visRect.height - worldmap.getHeight(),
                    this);
            wmInteriorRect.x = visRect.x + (worldmap.getWidth() - wmInteriorRect.width)/2;
            wmInteriorRect.y = visRect.y+visRect.height-worldmap.getHeight() +
                    (worldmap.getHeight() - wmInteriorRect.height)/2;

            //draw the outline of the viewport
            g2.setColor(Color.black);
            double hRatio = wmInteriorRect.getWidth()/pref.getWidth();
            double vRatio = wmInteriorRect.getHeight()/pref.getHeight();
            int hBump = worldmap.getWidth()-wmInteriorRect.width;
            int vBump = worldmap.getHeight()-wmInteriorRect.height;
            //bump a few pixels to avoid drawing on the border
            g2.drawRect((int)(visRect.x*hRatio)+hBump/2+visRect.x,
                    (int)(visRect.y*vRatio)+vBump/2+(visRect.y + visRect.height - worldmap.getHeight()),
                    (int)(visRect.width*hRatio),
                    (int)(visRect.height*vRatio));
        }


        //see if the user has right-clicked to popup some marker info
        if(popupDrawPoint != null){

            //dumb bug where little datasets popup the box in the wrong place
            int smallDatasetSlopH = 0;

            if (pref.getWidth() < visRect.width){
                smallDatasetSlopH = (int)(visRect.width - pref.getWidth())/2;
            }

            g2.setFont(popupFont);
            FontMetrics popupMetrics = g2.getFontMetrics();

            int maxStrWidth = 0;
            for (int x = 0; x < displayStrings.size(); x++){
                if (maxStrWidth < popupMetrics.stringWidth((String)displayStrings.elementAt(x))){
                    maxStrWidth = popupMetrics.stringWidth((String)displayStrings.elementAt(x));
                }
            }
            //edge shifts prevent window from popping up partially offscreen
            int visRightBound = (int)(getVisibleRect().getWidth() + getVisibleRect().getX());
            int visBotBound = (int)(getVisibleRect().getHeight() + getVisibleRect().getY());
            int rightEdgeShift = 0;
            if (popupDrawPoint.x + maxStrWidth + popupHorizMargin*2 > visRightBound){
                rightEdgeShift = popupDrawPoint.x + maxStrWidth + popupHorizMargin*2 - visRightBound;
            }
            popupDrawPoint.x = popupDrawPoint.x - rightEdgeShift;


            int botEdgeShift = 0;
            if (popupDrawPoint.y + displayStrings.size()*popupMetrics.getHeight()+10 > visBotBound){
                botEdgeShift = popupDrawPoint.y + displayStrings.size()*popupMetrics.getHeight()+15 - visBotBound;
            }

            popupDrawPoint.y = popupDrawPoint.y-botEdgeShift;

            g2.setColor(Color.white);
            g2.fillRect(popupDrawPoint.x+1-smallDatasetSlopH,
                    popupDrawPoint.y+1,
                    maxStrWidth + 2*popupHorizMargin,
                    displayStrings.size()*popupMetrics.getHeight()+popupMetrics.getDescent());
            g2.setColor(Color.black);
            g2.drawRect(popupDrawPoint.x-smallDatasetSlopH,
                    popupDrawPoint.y,
                    maxStrWidth + 2*popupHorizMargin,
                    displayStrings.size()*popupMetrics.getHeight()+popupMetrics.getDescent());


            for (int x = 0; x < displayStrings.size(); x++){
                g.drawString((String)displayStrings.elementAt(x),popupDrawPoint.x + popupHorizMargin-smallDatasetSlopH,
                        popupDrawPoint.y+((x+1)*popupMetrics.getHeight()));
            }
        }


        // draw the cached last right-click selection
        // The purpose of testing for empty string is just to avoid an 2-unit empty white box
        if (lastSelection != null){
            if ((zoomLevel == 0) && (!lastSelection.equals("")) && (!forExport))
            {
                g2.setFont(boxFont);
                // a bit extra on all side
                int last_descent = g2.getFontMetrics().getDescent();
                int last_box_x = (visRect.x + LAST_SELECTION_LEFT) - 2;
                int last_box_y = (visRect.y - g2.getFontMetrics().getHeight() + LAST_SELECTION_TOP + last_descent) - 1 ;
                int last_box_width = g2.getFontMetrics().stringWidth(lastSelection) + 4;
                int last_box_height = g2.getFontMetrics().getHeight() + 2;
                g2.setColor(Color.white);
                g2.fillRect(last_box_x, last_box_y, last_box_width, last_box_height);
                g2.setColor(Color.black);
                g2.drawRect(last_box_x, last_box_y, last_box_width, last_box_height);
                g2.drawString(lastSelection, LAST_SELECTION_LEFT + visRect.x, LAST_SELECTION_TOP + visRect.y);
            }
        }


        //see if we're drawing a worldmap resize rect
        if (resizeWMRect != null){
            g2.setColor(Color.black);
            g2.drawRect(resizeWMRect.x,
                    resizeWMRect.y,
                    resizeWMRect.width,
                    resizeWMRect.height);
        }

        //see if we're drawing a block selector rect
        if (blockRect != null){
            g2.setColor(Color.black);
            g2.setStroke(dashedThinStroke);
            g2.drawRect(blockRect.x, blockRect.y,
                    blockRect.width, blockRect.height);
        }
    }

    public double[] doMarkerLayout(double[] snpPositions, double goalSpan){
        //create an array for the projected positions, initialized to starting positions
        double spp[] = new double[snpPositions.length];
        System.arraycopy(snpPositions, 0, spp, 0, spp.length);

        /*
        Create some simple structures to keep track of which snps are bumping into each other (and whose
        positions are dependent on each other)
        */
        BitSet[] conflicts = new BitSet[snpPositions.length];
        for (int i=0; i<conflicts.length; ++i) {
            conflicts[i] = new BitSet();
            conflicts[i].set(i);
        }

        while (true) {
            boolean trouble = false;
            for (int i=0; i<spp.length-1; ++i) {

                //if two SNPs are overlapping (i.e. centers are < boxSize apart)
                if (spp[i+1]-spp[i]<boxSize-.0001) {
                    trouble = true;

                    //update the bump structures .. these two snps now bump (and have positions that are
                    //dependent on each other) .. indicate that in the bump structure
                    int ip = i+1;
                    conflicts[i].set(ip);
                    conflicts[ip].set(i);

                    //Come up with the full set all snps that are involved in a bump/dependency with either
                    //of these two snps
                    BitSet full = new BitSet();
                    for (int j=0; j<conflicts[i].size(); ++j) {
                        if (conflicts[i].get(j)) full.set(j);
                    }
                    for (int j=0; j<conflicts[ip].size(); ++j) {
                        if (conflicts[ip].get(j)) full.set(j);
                    }

                    /*
                    decide on the bounds of this full set of snps for which a bump problem exists
                    each snp inherits this full set of snps for its bump/dependency structure
                    */
                    int li = -1;
                    int hi = -1;
                    int conflict_count=0;
                    for (int j=0; j<full.size(); ++j) {
                        if (full.get(j)) {
                            conflicts[j] = (BitSet)full.clone();
                            if (li==-1) {li=j;}
                            hi=j;
                            conflict_count++;
                        }
                    }

                    //reposition the projected positions of the bumping snps, centered over
                    //the non-projected snp range of that set of snps .. with boundary conditions
                    double total_space_to_be_spanned = boxSize*(conflict_count-1);
                    double low_point = snpPositions[li];
                    double high_point = snpPositions[hi];
                    double first_snp_proj_pos = low_point - (total_space_to_be_spanned-(high_point-low_point))/2;
                    if (first_snp_proj_pos<0.0) first_snp_proj_pos=0.0;
                    if (first_snp_proj_pos+total_space_to_be_spanned>goalSpan) {first_snp_proj_pos = goalSpan-total_space_to_be_spanned;}
                    for (int j=li; j<=hi; ++j) {
                        spp[j] = first_snp_proj_pos + boxSize*(j-li);
                    }
                    break;
                }
            }
            if (!trouble) break;
        }
        return spp;
    }

    public void computePreferredSize(){
        this.computePreferredSize(this.getGraphics());
    }

    public void computePreferredSize(Graphics g) {
        if (Chromosome.getSize() == 0){
            //no valid markers so return an empty size
            setPreferredSize(new Dimension(0,0));
            return;
        }

        //setup marker positions
        //force it to run through the aligner once by setting this val as negative
        double aligned = -1;
        long minpos = Chromosome.getMarker(0).getPosition();
        long maxpos = Chromosome.getMarker(Chromosome.getSize()-1).getPosition();
        double spanpos = maxpos - minpos;
        double[] initialPositions = new double[Chromosome.getSize()];
        alignedPositions = new double[Chromosome.getSize()];
        double lineSpan = (Chromosome.getSize()-1) * boxSize;

        //keep trying until we've got at least a certain fraction of the markers aligned
        while (aligned < Options.getSpacingThreshold()){
            double numAligned = 0;
            for (int i = 0; i < initialPositions.length; i++){
                initialPositions[i] = (lineSpan*((Chromosome.getMarker(i).getPosition()-minpos)/spanpos));
            }
            alignedPositions = doMarkerLayout(initialPositions, lineSpan);
            for (int i = 0; i < initialPositions.length; i++){
                //if the alignedPos is less than two pixels from the intitialpos we
                //decide that's "close enough" to being aligned
                if (initialPositions[i] == alignedPositions[i])
                    numAligned++;
            }
            aligned = numAligned/initialPositions.length;
            //if we haven't finished yet we want to try again with a longer line...
            lineSpan += 0.05 * lineSpan;
        }
        double gblineSpan = alignedPositions[alignedPositions.length-1] - alignedPositions[0];

        //generate gbrowse image if appropriate
        int gbImageHeight = 0;
        if (Options.isGBrowseShown() && Chromosome.getDataChrom() != null && !Chromosome.getDataChrom().equalsIgnoreCase("none")){
            try{
                long gbleft, gbright;
                if (Options.getgBrowseLeft() != 0 || Options.getgBrowseRight() != 0){
                    gbleft = Options.getgBrowseLeft();
                    gbright = Options.getgBrowseRight()+1;
                }else{
                    gbleft = minpos;
                    gbright = maxpos+1;
                }
                String dataBuild;
                if (!Chromosome.getDataBuild().equalsIgnoreCase("none")){
                    dataBuild = "_" + Chromosome.getDataBuild().substring(5).toUpperCase();
                }else{
                    dataBuild = "_B35";
                }

                String gChrom;
                if (Chromosome.getDataChrom().equalsIgnoreCase("chrp")){ //account for pseudoautosomal
                    gChrom = "chrx";
                }else{
                    gChrom = Chromosome.getDataChrom();
                }

                //A NOTE ON VERY WIDE IMAGES:
                //hapmap.org freaks out if image request is > 32717 pixels wide, because it adds 50
                //for a margin, and evidently can't draw something wider than the max value of a short
                //so if we really want something that big, we break the request into several
                //adjacent chunks below. If the image size is < 30K then the code below
                //should reduce to doing what it used to, i.e. get just one image
                //interestingly, java has a similar problem with images of type INT_RGB, despite
                //the "INT" in the name, so we use type INT_ARGB which doesn't have this issue.
                // --jcb

                Vector imageVector = new Vector();
                int numChunks = (int)(Math.ceil(gblineSpan/MAX_GBROWSE_WIDTH));
                long chunkSize = (long)((MAX_GBROWSE_WIDTH/gblineSpan) * (gbright - gbleft));
                long[] chunkLefts = new long[numChunks];
                long[] chunkRights = new long[numChunks];
                double[] chunkSpans = new double[numChunks];
                for (int i = 0; i < numChunks; i++){
                    chunkLefts[i] = gbleft + i*chunkSize;
                    chunkRights[i] = chunkLefts[i] + chunkSize;
                    chunkSpans[i] = MAX_GBROWSE_WIDTH;
                }
                //remainder, or cases when width is less than max allowed
                if (numChunks > 0){
                    chunkRights[numChunks-1] = gbright;
                    chunkSpans[numChunks-1] = gblineSpan - MAX_GBROWSE_WIDTH*(numChunks-1);
                }

                int totalHeight = 0;
                int totalWidth = 0;
                for (int i = 0; i < numChunks; i++){
                    URL imageUrl = new URL("http://www.hapmap.org/cgi-perl/gbrowse/gbrowse_img/hapmap" + dataBuild + "/?name=" +
                            gChrom + ":" + chunkLefts[i] + ".." + chunkRights[i] + ";width=" + chunkSpans[i] +
                            ";type="+ Options.getgBrowseTypes() + ";options=" + Options.getgBrowseOpts() + ";nocache=1");
                    Toolkit toolkit = Toolkit.getDefaultToolkit();
                    HttpURLConnection con = (HttpURLConnection)imageUrl.openConnection();
                    con.setRequestProperty("User-agent",Constants.USER_AGENT);
                    con.connect();

                    int response = con.getResponseCode();

                    if ((response != HttpURLConnection.HTTP_ACCEPTED) && (response != HttpURLConnection.HTTP_OK)) {
                        //if something went wrong
                        throw new IOException("Could not connect to HapMap server.");
                    }

                    InputStream inputStream = con.getInputStream();
                    BufferedInputStream bis = new BufferedInputStream(inputStream);
                    byte[] buf = new byte[2048];
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    BufferedOutputStream bos = new BufferedOutputStream(baos);
                    int bytesRead;
                    while ((bytesRead = bis.read(buf,0,buf.length)) != -1){
                        bos.write(buf,0,bytesRead);
                    }
                    bos.flush();
                    bos.close();
                    bis.close();

                    Image img = toolkit.createImage(baos.toByteArray());
                    MediaTracker mt = new MediaTracker(this);
                    mt.addImage(img,0);
                    setCursor(new Cursor(Cursor.WAIT_CURSOR));
                    mt.waitForID(0);
                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    imageVector.add(img);

                    if (img.getHeight(this) > totalHeight){
                        totalHeight = img.getHeight(this);
                    }
                    totalWidth += img.getWidth(this);
                }

                if (totalWidth > 0 && totalHeight > 0){
                    gBrowseImage = new BufferedImage(totalWidth,totalHeight,BufferedImage.TYPE_INT_ARGB);
                    gBrowseImage.getGraphics().setColor(Color.white);
                    gBrowseImage.getGraphics().fillRect(0,0,gBrowseImage.getWidth(),gBrowseImage.getHeight());
                    int bumper = 0;
                    for (int j = 0; j < imageVector.size(); j++){
                        Image img = (Image)imageVector.get(j);
                        gBrowseImage.getGraphics().drawImage(img,bumper,0, this);
                        bumper += chunkSpans[j];
                    }
                    gbImageHeight = gBrowseImage.getHeight(this) + TRACK_GAP; // get height so we can shift everything down
                }else{
                    //couldn't get the image for whatever reason.
                    JOptionPane.showMessageDialog(theHV,
                            "An error occured while accessing the HapMap website.\n",
                            "HapMap Info Track",
                            JOptionPane.ERROR_MESSAGE);
                    gBrowseImage = null;
                    Options.setShowGBrowse(false);
                }
            }catch (IOException e){
                //couldn't get the image for whatever reason.
                JOptionPane.showMessageDialog(theHV,
                        "An error occurred while accessing the HapMap website.\n"+e.getMessage(),
                        "HapMap Info Track",
                        JOptionPane.ERROR_MESSAGE);
                Options.setShowGBrowse(false);
            }catch (InterruptedException e){
                //just in case something awful happens
                gBrowseImage = null;
                Options.setShowGBrowse(false);
            }
        }



        //loop through table to find deepest non-null comparison
        DPrimeTable dPrimeTable = theData.dpTable;
        int upLim, loLim;
        if (forExport){
            loLim = exportStart;
            upLim = exportStop;
        }else{
            loLim = 0;
            upLim = Chromosome.getSize()-1;
        }
        double sep = 0;
        for (int x = loLim; x < upLim; x++){
            for (int y = x+1; y <= upLim; y++){
                if (dPrimeTable.getLDStats(x,y) != null){
                    if (sep < alignedPositions[y]-alignedPositions[x]){
                        sep = alignedPositions[y]-alignedPositions[x];
                    }
                }
            }
        }
        //add one so we don't clip bottom box
        sep += boxSize;


        if (g != null){
            g.setFont(markerNameFont);
            FontMetrics fm = g.getFontMetrics();
            if (printMarkerNames){
                blockDispHeight = boxSize/3 + fm.getHeight();
            }else{
                blockDispHeight = boxSize/3;
            }
        }

        //"high" represents the total height of the panel. "infoheight" is the total height of the
        //header info above the LD plot. When we draw the worldmap we want to nudge the LD plot down
        //by a scaled factor of infoheight so that clicking lines it up properly.
        int high = (int)(sep/2) + V_BORDER*2 + blockDispHeight;
        if (theData.infoKnown){
            infoHeight = TICK_HEIGHT + TICK_BOTTOM + widestMarkerName + TEXT_GAP + gbImageHeight;
        }else{
            infoHeight = 0;
        }
        if (theData.trackExists){
            //make room for analysis track at top
            infoHeight += TRACK_HEIGHT + TRACK_GAP;
        }
        high += infoHeight;

        int wide = 2*H_BORDER + (int)(alignedPositions[upLim] - alignedPositions[loLim]);
        //this dimension is just the area taken up by the dprime chart
        //it is used in drawing the worldmap
        //for other elements add their heights in the next code hunk!
        chartSize = new Dimension(wide, high);


        Rectangle visRect = getVisibleRect();
        //big datasets often scroll way offscreen in zoom-out mode
        //but aren't the full height of the viewport
        if (high < visRect.height && showWM && !forExport){
            high = visRect.height;
        }
        if (!getPreferredSize().equals(new Dimension(wide,high))){
            noImage=true;

        }
        setPreferredSize(new Dimension(wide, high));
        if (getParent() != null){
            JViewport par = (JViewport)getParent();
            //OK, if the resizing involves a dataset which is larger than the visible Rect we need to prod the
            //Viewport into resizing itself. if the resizing is all within the visrect, we don't want to do this
            //because it makes the screen flicker with a double-repaint.
            if (par.getVisibleRect().width < par.getViewSize().width ||
                    par.getVisibleRect().height < par.getViewSize().height){
                par.setViewSize(getPreferredSize());
            }
        }
    }

    public int getBoundaryMarker(double pos){
        //if pos is in the array the binarysearch returns the positive index
        //otherwise it returns the negative "insertion index" - 1
        int where = Arrays.binarySearch(alignedPositions, pos);
        if (where >= 0){
            return where;
        }else{
            return -where - 1;
        }
    }

    public int getPreciseMarkerAt(double pos){
        int where = Arrays.binarySearch(alignedPositions,pos);
        if (where >= 0){
            return where;
        }else{
            int left = -where-2;
            int right = -where-1;
            if (left < 0){
                left = 0;
                right = 1;
            }
            if (right >= alignedPositions.length){
               right = alignedPositions.length-1;
                left = alignedPositions.length-1;
            }
            if (Math.abs(alignedPositions[right] - pos) < boxRadius){
                return right;
            }else if (Math.abs(pos - alignedPositions[left]) < boxRadius){
                return left;
            } else{
                //out of bounds
                return -1;
            }
        }
    }

    public void mouseClicked(MouseEvent e) {
        if ((e.getModifiers() & InputEvent.BUTTON1_MASK) ==
                InputEvent.BUTTON1_MASK) {
            int clickX = e.getX();
            int clickY = e.getY();
            if (showWM && wmInteriorRect.contains(clickX,clickY)){
                //convert a click on the worldmap to a point on the big picture
                int bigClickX = (((clickX - getVisibleRect().x - (worldmap.getWidth()-wmInteriorRect.width)/2)
                        * chartSize.width) /
                        wmInteriorRect.width)-getVisibleRect().width/2;
                int bigClickY = (((clickY - getVisibleRect().y -
                        (worldmap.getHeight() - wmInteriorRect.height)/2 -
                        (getVisibleRect().height-worldmap.getHeight())) *
                        chartSize.height) / wmInteriorRect.height) -
                        getVisibleRect().height/2;

                //if the clicks are near the edges, correct values
                if (bigClickX > chartSize.width - getVisibleRect().width){
                    bigClickX = chartSize.width - getVisibleRect().width;
                }
                if (bigClickX < 0){
                    bigClickX = 0;
                }
                if (bigClickY > chartSize.height - getVisibleRect().height){
                    bigClickY = chartSize.height - getVisibleRect().height;
                }
                if (bigClickY < 0){
                    bigClickY = 0;
                }

                ((JViewport)getParent()).setViewPosition(new Point(bigClickX,bigClickY));
            }else{
                theHV.changeBlocks(BLOX_CUSTOM);
                Rectangle2D blockselector = new Rectangle2D.Double(clickXShift-boxRadius,clickYShift - boxRadius,
                        alignedPositions[alignedPositions.length-1] + boxSize, boxSize);
                if(blockselector.contains(clickX,clickY)){
                    int whichMarker = getPreciseMarkerAt(clickX - clickXShift);
                    if (whichMarker > -1){
                        if (theData.isInBlock[whichMarker]){
                            theData.removeFromBlock(whichMarker);
                            repaint();
                        } else if (whichMarker > 0 && whichMarker < Chromosome.realIndex.length){
                            theData.addMarkerIntoSurroundingBlock(whichMarker);
                        }
                    }
                }
            }
        }
    }

    public void mousePressed (MouseEvent e) {
        Rectangle blockselector = new Rectangle(clickXShift-boxRadius,clickYShift - boxRadius,
                (int)alignedPositions[alignedPositions.length-1]+boxSize, boxSize);

        //if users right clicks & holds, pop up the info
        if ((e.getModifiers() & InputEvent.BUTTON3_MASK) ==
                InputEvent.BUTTON3_MASK){
            Graphics g = getGraphics();
            g.setFont(popupFont);
            //FontMetrics metrics = g.getFontMetrics();
            DPrimeTable dPrimeTable = theData.dpTable;
            final int clickX = e.getX();
            final int clickY = e.getY();
            final int boxX, boxY;
            boxX = getPreciseMarkerAt(clickX - clickXShift - (clickY-clickYShift));
            boxY = getPreciseMarkerAt(clickX - clickXShift + (clickY-clickYShift));
            displayStrings = null;

            if ((boxX >= lowX && boxX <= highX) &&
                    (boxY > boxX && boxY < highY) &&
                    !(wmInteriorRect.contains(clickX,clickY))){
                if (dPrimeTable.getLDStats(boxX,boxY) != null){
                    double[] freqs = dPrimeTable.getLDStats(boxX,boxY).getFreqs();

                    displayStrings = new Vector();
                    currentSelection = new String ("Last Selection: ("); // update the cached value
                    if (theData.infoKnown){
                        displayStrings.add(new String ("(" +Chromosome.getMarker(boxX).getDisplayName() +
                                ", " + Chromosome.getMarker(boxY).getDisplayName() + ")"));
                        double sep = (int)((Chromosome.getMarker(boxY).getPosition() -
                                Chromosome.getMarker(boxX).getPosition())/100);
                        sep /= 10;
                        displayStrings.add(new Double(sep).toString() + " kb");
                        currentSelection += Chromosome.getMarker(boxX).getName() +
                                ", " + Chromosome.getMarker(boxY).getName();
                    }else{
                        displayStrings.add(new String("(" + (Chromosome.realIndex[boxX]+1) + ", " +
                                (Chromosome.realIndex[boxY]+1) + ")"));
                        currentSelection += new String((Chromosome.realIndex[boxX]+1) +
                                 ", " + (Chromosome.realIndex[boxY]+1));
                    }
                    displayStrings.add(new String ("D': " + dPrimeTable.getLDStats(boxX,boxY).getDPrime()));
                    displayStrings.add(new String ("LOD: " + dPrimeTable.getLDStats(boxX,boxY).getLOD()));
                    displayStrings.add( new String ("r-squared: " + dPrimeTable.getLDStats(boxX,boxY).getRSquared()));
                    displayStrings.add(new String ("D' conf. bounds: " +
                            dPrimeTable.getLDStats(boxX,boxY).getConfidenceLow() + "-" +
                            dPrimeTable.getLDStats(boxX,boxY).getConfidenceHigh()));
                    currentSelection += ")  -   D': " + dPrimeTable.getLDStats(boxX,boxY).getDPrime() +
                            "   LOD: " + dPrimeTable.getLDStats(boxX,boxY).getLOD() +
                            "   r-squared: " + dPrimeTable.getLDStats(boxX,boxY).getRSquared();



                    //get the alleles for the 4 two-marker haplotypes
                    String[] alleleStrings = new String[4];
                    String[] alleleMap = {"X", "A","C","G","T"};
                    if (freqs[0] + freqs[1] > freqs[2] + freqs[3]){
                        alleleStrings[0] = alleleMap[Chromosome.getMarker(boxX).getMajor()];
                        alleleStrings[1] = alleleMap[Chromosome.getMarker(boxX).getMajor()];
                        alleleStrings[2] = alleleMap[Chromosome.getMarker(boxX).getMinor()];
                        alleleStrings[3] = alleleMap[Chromosome.getMarker(boxX).getMinor()];
                    }else{
                        alleleStrings[0] = alleleMap[Chromosome.getMarker(boxX).getMinor()];
                        alleleStrings[1] = alleleMap[Chromosome.getMarker(boxX).getMinor()];
                        alleleStrings[2] = alleleMap[Chromosome.getMarker(boxX).getMajor()];
                        alleleStrings[3] = alleleMap[Chromosome.getMarker(boxX).getMajor()];
                    }
                    if (freqs[0] + freqs[3] > freqs[1] + freqs[2]){
                        alleleStrings[0] += alleleMap[Chromosome.getMarker(boxY).getMajor()];
                        alleleStrings[1] += alleleMap[Chromosome.getMarker(boxY).getMinor()];
                        alleleStrings[2] += alleleMap[Chromosome.getMarker(boxY).getMinor()];
                        alleleStrings[3] += alleleMap[Chromosome.getMarker(boxY).getMajor()];
                    }else{
                        alleleStrings[0] += alleleMap[Chromosome.getMarker(boxY).getMinor()];
                        alleleStrings[1] += alleleMap[Chromosome.getMarker(boxY).getMajor()];
                        alleleStrings[2] += alleleMap[Chromosome.getMarker(boxY).getMajor()];
                        alleleStrings[3] += alleleMap[Chromosome.getMarker(boxY).getMinor()];
                    }

                    displayStrings.add(new String("Frequencies:"));
                    for (int i = 0; i < 4; i++){
                        if (freqs[i] > 1.0E-10){
                            displayStrings.add( new String(alleleStrings[i] + " = " +
                                    Math.rint(1000 * freqs[i])/10 + "%"));
                        }
                    }
                }
            } else if (blockselector.contains(clickX, clickY)){
                int marker = getPreciseMarkerAt(clickX - clickXShift);
                if (marker >= 0){
                    displayStrings = new Vector();
                    currentSelection = new String ("Last Selection: "); // update the cached value

                    if (theData.infoKnown){
                        displayStrings.add(new String (Chromosome.getMarker(marker).getDisplayName()));
                        currentSelection += Chromosome.getMarker(marker).getName();
                    }else{
                        displayStrings.add(new String("Marker " + (Chromosome.realIndex[marker]+1)));
                        currentSelection += new String("Marker " + (Chromosome.realIndex[marker]+1));
                    }
                    displayStrings.add(new String ("MAF: " + Chromosome.getMarker(marker).getMAF()));
                    if (Chromosome.getMarker(marker).getExtra() != null)
                        displayStrings.addAll(Chromosome.getMarker(marker).getExtra());
                    currentSelection += new String (", MAF: " + Chromosome.getMarker(marker).getMAF());
                }
            }
            if (displayStrings != null){
                popupDrawPoint = new Point(clickX,clickY);
                repaint();
            }
        }else if ((e.getModifiers() & InputEvent.BUTTON1_MASK) ==
                InputEvent.BUTTON1_MASK){
            // clear the last selection if the mouse is left clicked
            lastSelection = new String ("");

            int x = e.getX();
            int y = e.getY();
            if (blockselector.contains(x,y)){
                setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                blockStartX = x;
            }
        }
    }

    public void mouseReleased(MouseEvent e) {
        if ((e.getModifiers() & InputEvent.BUTTON3_MASK) ==
                InputEvent.BUTTON3_MASK){
            //remove popped up window
            popupDrawPoint = null;

            //cache last selection.
            lastSelection = currentSelection;
            currentSelection = null;

            repaint();
        } else if ((e.getModifiers() & InputEvent.BUTTON1_MASK) ==
                InputEvent.BUTTON1_MASK){
            //resize window once user has ceased dragging
            if (getCursor() == Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)){
                noImage = true;
                if (resizeWMRect.width > 20){
                    wmMaxWidth = resizeWMRect.width;
                }
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                resizeWMRect = null;
                repaint();
            }
            if (getCursor() == Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)){
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                if (blockRect != null){
                    //don't add the block if the dragging was really short, as it was probably just a twitch while clicking
                    if (Math.abs(e.getX() - blockStartX) > boxRadius/2){
                        int firstMarker = getPreciseMarkerAt(blockStartX - clickXShift);
                        int lastMarker = getPreciseMarkerAt(e.getX() - clickXShift);
                        //we're moving left to right
                        if (blockStartX > e.getX()){
                            int temp = firstMarker;
                            firstMarker = lastMarker;
                            lastMarker = temp;
                        }
                        //negative results represent starting or stopping the drag in "no-man's land"
                        //so we adjust depending on which side we're on
                        if (firstMarker < 0){
                            firstMarker = -firstMarker + 1;
                        }
                        if (lastMarker < 0){
                            lastMarker = -lastMarker;
                        }
                        theHV.changeBlocks(BLOX_CUSTOM);
                        theData.addBlock(firstMarker, lastMarker);
                    }
                    blockRect = null;
                    repaint();
                }
            }

        }
    }

    public void mouseDragged(MouseEvent e) {
        if ((e.getModifiers() & InputEvent.BUTTON1_MASK) ==
                InputEvent.BUTTON1_MASK) {
            //conveniently, we can tell what do do with the drag event
            //based on what the cursor is
            if (getCursor() == Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)){
                int width = e.getX() - wmInteriorRect.x;
                double ratio = (double)width/(double)worldmap.getWidth();
                int height = (int)(ratio*worldmap.getHeight());

                resizeWMRect = new Rectangle(wmInteriorRect.x+1,
                        wmInteriorRect.y + wmInteriorRect.height - height,
                        width,
                        height-1);
                repaint();
            }else if (getCursor() == Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)){
                Rectangle r = getVisibleRect();

                int xcorner,width;
                if (e.getX() < blockStartX){
                    if (e.getX() < r.x +2){
                        scrollRectToVisible(new Rectangle(r.x -25, r.y, r.width, 1));
                    }
                    //we're dragging right to left, so flip it.
                    xcorner = e.getX() - clickXShift + left;
                    width =  blockStartX - e.getX();
                }else{
                    if (e.getX() > r.x + r.width - 2){
                        scrollRectToVisible(new Rectangle(r.x+25,r.y,r.width,1));
                    }
                    xcorner = blockStartX - clickXShift + left;
                    width = e.getX() - blockStartX;
                }
                blockRect = new Rectangle(xcorner, top - boxRadius/2 - TEXT_GAP,
                        width,boxRadius);
                repaint();
            }
        }
    }

    public void mouseMoved(MouseEvent e){
        //when the user mouses over the corner of the worldmap, change the cursor
        //to the resize cursor
        if (getCursor() == Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)){
            if (wmResizeCorner.contains(e.getPoint())){
                setCursor(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR));
            }
        } else if (getCursor() == Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)){
            if (!(wmResizeCorner.contains(e.getPoint()))){
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        }
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

}

