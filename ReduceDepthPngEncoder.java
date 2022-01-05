import java.io.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import java.awt.image.BufferedImage;
import javax.imageio.*;

/**A class that defines a RGB triplet
*/
class RGB
{
    /**an array to store image lookup values
    */
    private int[] values;
    
    /**constructor
    *@param argb the integer that represents argb values
    */
    public RGB(int argb)
    {
        this((argb & 0xff0000) >> 16,(argb & 0xff00)>>8,argb & 0xff);
    }
    
    /**constructor
    *@param r the red value
    *@param g the green value
    *@param b the blue value
    */
    public RGB(int r, int g, int b)
    {
        values = new int[3];
        set(r,g,b);
    }
    
    /**default constructor that sets r=0,g=0,b=0
    */
    public RGB()
    {
        this(0,0,0);
    }
    
    /**copy constructor
    */
    public RGB(RGB rhs)
    {
        this(rhs.values[0],rhs.values[1],rhs.values[2]);
    }
    
    /**@return the red value
    */
    public int getRed()
    {
        return values[0];
    }
    
    /**@return the green value
    */
    public int getGreen()
    {
        return values[1];
    }
    
    /**@return the blue value
    */
    public int getBlue()
    {
        return values[2];
    }
    
    public void set(int r, int g, int b)
    {
        values[0] = r;;
        values[1] = g;
        values[2] = b;
    }
    
    /**Set the rgb values
    *@param r the red value
    *@param g the green value
    *@param b the blue value
    */
    public void set(RGB rhs)
    {
        set(rhs.values[0],rhs.values[1],rhs.values[2]);
    }
    
    /**add another RGB to this value
    *@param rhs the rhs value to be added
    */
    public void add(RGB rhs)
    {
        for(int i=0;i<3;i++)
        {
            this.values[i] = this.values[i]+rhs.values[i];
        }
    }
    
    /**@return the squared distance between this and rhs
    *@param rhs the rhs value
    */
    public int squaredDistance(RGB rhs)
    {
        int sum = 0;
        for(int i=0;i<3;i++)
        {
            sum = sum + (this.values[i]-rhs.values[i])*(this.values[i]-rhs.values[i]);
        }
        return sum;
    }
    
    /**Scaled the triplet
    *@param k the scale factor
    */
    public void scale(double k)
    {
        for(int i=0;i<3;i++)
        {
            this.values[i] = (int)Math.round(values[i]*k);
        }
    }
    
    @Override
    public int hashCode()
    {
        int value = 0;
        value = value | (values[0]<<16);
        value = value | (values[1]<<8);
        value = value | (values[2]);
        return value;
    }
    
    @Override
    public boolean equals(Object rhs)
    {
        try
        {
            RGB o = (RGB)rhs;
            return (this.values[0]==o.values[0]) && (this.values[1]==o.values[1]) && (this.values[2]==o.values[2]);
        }
        catch(ClassCastException e)
        {
            return false;
        }
    }
    @Override
    public String toString()
    {
        return "r="+values[0]+",g="+values[1]+",b="+values[2];
    }
}

/**A class that defines a cluster
*/
class Cluster
{
    /**The list of rgbs in this cluster
    */
    private ArrayList<RGB> values;
    
    /**the mean RGB value for this clusrter
    */
    private RGB mean;
    
    /**create a new cluster
    *@param mean the mean value for this newly created cluster
    */
    public Cluster(RGB mean)
    {
        this.mean = mean;
        values = new ArrayList<RGB>();
    }
    
    /**@return the mean
    */
    public RGB getMean()
    {
        return mean;
    }
    
    /**Clear the list of values
    */
    public void clear()
    {
        values.clear();
    }
    
    /**Add a rgb to the list
    *@param value the value to be added
    */
    public void addValue(RGB value)
    {
        values.add(value);
    }
    
    /**@return the sqaure distance betwen a value and this cluster
    *@param value the value
    */
    public int distance(RGB value)
    {
        return mean.squaredDistance(value);
    }
    
    /**recalculate the mean
    *@return true if the mean had changed
    */
    public boolean recalculate()
    {
        RGB currentMean = new RGB(mean);
        mean.set(0,0,0);
        
        for(RGB value:values)
        {
            mean.add(value);
        }
        if (values.size()>0)
        {
            mean.scale(1.0/values.size());
        }
        return !mean.equals(currentMean);
    }
}

/**A class to reduce the bits depth of an image
*/
public class ReduceDepthPngEncoder
{
    /**the final image
    */
    private byte[][] image;
    
    /**the palette to represent the final image
    *Must be of size 2^n
    */
    private int[][] palette;
    
    /**the bits depth of the image
    */
    private int bitsDepth;
    
    /**the width of the image
    */
    private int width;
    
    /**the height of the image
    */
    private int height;
    
    /**the colour type of PNG file, since we are using palette, it must be 3
    */
    private int colourType;
    
    public ReduceDepthPngEncoder(BufferedImage inImage, int bitsDepth)
    {
        this.bitsDepth = bitsDepth;
        this.width = inImage.getWidth();
        this.height = inImage.getHeight();
        createPalette(inImage);
        colourType = 3;
        assign(inImage);
    }
    
    /**Create the palette
    *@param inImage the input image
    */
    private void createPalette(BufferedImage inImage)
    {
        int paletteSize = 1;
        for(int i=0;i<bitsDepth;i++)
        {
            paletteSize = paletteSize * 2;
        }        
        Cluster[] clusters= new Cluster[paletteSize];
        
        //generate random clusters locations
        for(int i=0;i<paletteSize;i++)
        {
            RGB rgb = new RGB((int)(Math.random()*0xFFFFFF),(int)(Math.random()*0xFFFFFF),(int)(Math.random()*0xFFFFFF));
            
            clusters[i] = new Cluster(rgb);
        }
        
        int iteration  = 1;
        boolean done = false;
        do
        {
            System.out.println("Iteration "+iteration );
            iteration++;
            //clear all clusters
            for(int p=0;p<paletteSize;p++)
            {
                clusters[p].clear();
            }
            for(int r=0;r<height;r++)
            {
                for(int c=0;c<width;c++)
                {
                    RGB rgb = new RGB(inImage.getRGB(c,r));
                    int bestCluster = 0;
                    int bestDistance = Integer.MAX_VALUE;
                    for(int p=0;p<paletteSize;p++)
                    {
                        int distance = clusters[p].distance(rgb);
                        if (distance<bestDistance)
                        {
                            bestCluster = p;
                            bestDistance = distance;
                        }
                    }
                    clusters[bestCluster].addValue(rgb);
                }
            }
            //recaculate mean
            //if no change then end
            done = true;
            for(int p=0;p<paletteSize;p++)
            {
                if (clusters[p].recalculate())
                {
                    done = false;
                }
                System.out.println("\t"+clusters[p].getMean());
            }
        }while(!done);
        
        palette = new int[paletteSize][3];
        for(int i=0;i<paletteSize;i++)
        {
            palette[i][0] = clusters[i].getMean().getRed();
            palette[i][1] = clusters[i].getMean().getGreen();
            palette[i][2] = clusters[i].getMean().getBlue();
        }
    }
    
    /**assign the palette colour
    *@param inImage the input image
    */
    private void assign(BufferedImage inImage)
    {
        image = new byte[height][width];
        for(int r=0;r<height;r++)
        {
            for(int c=0;c<width;c++)
            {
                RGB rgb = new RGB(inImage.getRGB(c,r));
                int bestIndex = 0;
                int bestSqauredDistance = Integer.MAX_VALUE;
                for(int p=0;p<palette.length;p++)
                {
                    RGB paletteRGB = new RGB(palette[p][0],palette[p][1],palette[p][2]);
                    int currentSqauredDistance = paletteRGB.squaredDistance(rgb);
                    if (currentSqauredDistance<bestSqauredDistance)
                    {
                        bestSqauredDistance = currentSqauredDistance;
                        bestIndex = p;
                    }
                }
                image[r][c] = (byte)bestIndex;
            }
        }
    }
    
    /**@return tghe PLTE chunk of PNG file specificiation
    */
    private byte[] getPLTEChunk()
    {
        //length 4 bytes
        //Name   4 bytes
        //Data   2^bit size * 3 bytes
        //CRC    4 bytes
        //Total  12 +  2^bit size * 3 bytes bytes
        int dataLength = palette.length*3;
        byte[] data = new byte[12+dataLength];
        
        {
            byte[] dataLengthBytes = ByteBuffer.allocate(4).putInt(dataLength).array();
            data[0] = dataLengthBytes[0];
            data[1] = dataLengthBytes[1];
            data[2] = dataLengthBytes[2];
            data[3] = dataLengthBytes[3];
        }
        
        data[4] = (byte)0x50;//P
        data[5] = (byte)0x4C;//L
        data[6] = (byte)0x54;//T
        data[7] = (byte)0x45;//E
        
        for(int i=0;i<palette.length;i++)
        {
            data[8+i*3] = (byte)palette[i][0];
            data[8+i*3+1] = (byte)palette[i][1];
            data[8+i*3+2] = (byte)palette[i][2];
        }
        
        calculateCRC32(data);
        return data;
    }
    
    /**@return the IDAT chunk of PNG file specificiation
    */
    private byte[] getImageData()
    {
        int rowDataLength = (int) Math.ceil(width*bitsDepth/8.0);
        //System.out.println("rowDataLength = "+rowDataLength);
        //System.out.println("width = "+width);
        int size = height*(rowDataLength+1);//each row has an addition byte for filltering, 0 means no filtering
		//System.out.println("Image data size="+size);
        byte[] data = new byte[size];
        int pos = 0;
        for(int r=0;r<height;r++)
        {
            //System.out.println("r="+r);
            data[pos] = 0;//no filter
            pos++;
            //int currentByte = 0;
            int currentByte = 0;
            int bitPos = 0;
            for(int c=0;c<width;c++)
            {
                byte value = image[r][c];
                for(int p=0;p<bitsDepth;p++)
                {
                    int bitValue = value>>(bitsDepth-p-1);
                    currentByte =  currentByte | (bitValue<<(7-bitPos));
                    bitPos++;
                    if (bitPos==8)
                    {
                        data[pos] = (byte)currentByte;
                        pos++;
                        currentByte = 0;
                        bitPos = 0;
                    }
                }
            }
            if (bitPos!=0)//did not flush byte in for loop, flush now
            {
                data[pos] = (byte)currentByte;
                pos++;
            }
        }
        return data;
    }
    
    /**@return the bytes of the png file
    */
    private byte[] getFileData()
    {
       //Signature 8
       //IHDR      25
       //PLTE <--insert before IDAT
       //IDAT
       //IEND 12
       byte[] idat = getIDATChunk();
       byte[] plte = getPLTEChunk();
       int total = 8+25+plte.length+idat.length+12;
       byte[] data = new byte[total];
       
       int pos=0;
       //copy Signature
       System.arraycopy(getPNGSignature(),0,data,pos,8);
       pos = pos + 8;
       //copy IHDR
       System.arraycopy(getIDHRChunk(),0,data,pos,25);
       pos = pos + 25;
       //copy PLTE
       System.arraycopy(plte,0,data,pos,plte.length);
       pos = pos + plte.length;
       //copy IDAT
       System.arraycopy(idat,0,data,pos,idat.length);
       pos = pos + idat.length;
       //copy IEND
       System.arraycopy(getIENDChunk(),0,data,pos,12);
	   /*System.out.println("Palette bytes="+plte.length);
	   System.out.println("IDAT bytes="+idat.length);
	   System.out.println("File data IDAT bytes="+data.length);*/
       return data;
    }
    
    /**@return the Signature chunk of PNG file specificiation
    */
    private static byte[] getPNGSignature()
    {
        byte[] data = {(byte)137,(byte)80,(byte)78,(byte)71,(byte)13,(byte)10,(byte)26,(byte)10};
        return data;
    }
    
    /**calculate and save the CRC32 of the chunk
    */
    private void calculateCRC32(byte[] data)
    {
        //CRC is calculate from chunk type code and chunk data fields
        //length is 4 bytes and last 4 bytes is CRC32
        int size = data.length-8;
        
        CRC32 crc = new CRC32();
        
        byte[] temp = new byte[size];
        System.arraycopy(data,4,temp,0,size);
        
        crc.update(temp);
        
        long crc32Value = crc.getValue();
        byte[] crcBytes = ByteBuffer.allocate(8).putLong(crc32Value).array();
        //first 4 bytes are 0
        data[data.length-4] = crcBytes[4];
        data[data.length-3] = crcBytes[5];
        data[data.length-2] = crcBytes[6];
        data[data.length-1] = crcBytes[7];
    }
    
    /**@return the IEND chunk of PNG file specificiation
    */
    private byte[] getIENDChunk()
    {
        byte[] data = new byte[12];
        data[0] =  (byte) 0x00;
        data[1] =  (byte) 0x00;
        data[2] =  (byte) 0x00;
        data[3] =  (byte) 0x00;
        data[4] =  (byte) 0x49;//I
        data[5] =  (byte) 0x45;//E
        data[6] =  (byte) 0x4E;//N
        data[7] =  (byte) 0x44;//D
        data[8] =  (byte) 0xAE;
        data[9] =  (byte) 0x42;
        data[10] = (byte) 0x60;
        data[11] = (byte) 0x82;
        return data;
    }
    
    /**@return the IDHR chunk of PNG file specificiation
    */
    private byte[] getIDHRChunk()
    {
        //length 4 bytes
        //Name   4 bytes
        //Data   13 bytes
        //CRC    4 bytes
        //Total  25 bytes
        byte[] data = new byte[25];
        data[0] = (byte)0;
        data[1] = (byte)0;
        data[2] = (byte)0;
        data[3] = (byte)13;
        data[4] = (byte)0x49;//I
        data[5] = (byte)0x48;//H
        data[6] = (byte)0x44;//D
        data[7] = (byte)0x52;//R
        
        
        byte[] widthBytes = ByteBuffer.allocate(4).putInt(width).array();
        data[8] = widthBytes[0];
        data[9] = widthBytes[1];
        data[10] = widthBytes[2];
        data[11] = widthBytes[3];
        byte[] heightBytes = ByteBuffer.allocate(4).putInt(height).array();
        data[12] = heightBytes[0];
        data[13] = heightBytes[1];
        data[14] = heightBytes[2];
        data[15] = heightBytes[3];
        
        data[16] = (byte) bitsDepth;
        data[17] = (byte) colourType;
        
        data[18] = (byte) 0;//compression method
        data[19] = (byte) 0;//Filter method
        data[20] = (byte) 0;//Interlace method
        
        
        calculateCRC32(data);
        
        return data;
    }
    
    /**@return the IDAT chunk of PNG file specificiation
    */
    private byte[] getIDATChunk()
    {
        final int bufferSize = 1024;
        
        byte[] imageData = getImageData();
        
        // Compress the bytes
        Deflater compresser = new Deflater();
        compresser.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);
        compresser.setInput(imageData);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(imageData.length);//should not be more than original length
        compresser.finish();
        byte[] buffer = new byte[bufferSize];
        while(!compresser.finished())
        {
            int count = compresser.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        byte[] compressedOutput = outputStream.toByteArray();
        int compressedDataLength = compressedOutput.length;
        
        
        //length 4 bytes
        //Name   4 bytes
        //Data   n bytes
        //CRC    4 bytes
        //Total  n+16 bytes
        byte[] data = new byte[compressedDataLength+16];
        
        byte[] lengthBytes = ByteBuffer.allocate(4).putInt(compressedDataLength).array();
        data[0] = lengthBytes[0];
        data[1] = lengthBytes[1];
        data[2] = lengthBytes[2];
        data[3] = lengthBytes[3];
        
        data[4] = (byte)0x49;//I
        data[5] = (byte)0x44;//D
        data[6] = (byte)0x41;//A
        data[7] = (byte)0x54;//T
        
        System.arraycopy(compressedOutput,0,data,8,compressedDataLength);
        
        calculateCRC32(data);
        return data;
    }
    

    /**Save the image
    *@param the filename to be saved
    */
    public void save(String filename)
        throws IOException
    {
       FileSystem fs = FileSystems.getDefault();
       Path path = fs.getPath(filename);
       Files.write(path,getFileData(),StandardOpenOption.CREATE);
    }
    
    public static void main(String[] args)
    {
        try
        {
            if (args.length<3)
            {
                System.out.println("Usage: java ReduceDepthPngEncoder input_image bits_depth output_image");
                return;
            }
            String inFilename = args[0];
            int bitsDepth = Integer.parseInt(args[1]);
            String outFilename = args[2];
            
System.out.println(inFilename);
            System.out.println(bitsDepth);
            System.out.println(outFilename);
            
            BufferedImage in = ImageIO.read(new File(inFilename));
            
            ReduceDepthPngEncoder encoder = new ReduceDepthPngEncoder(in,bitsDepth);
            encoder.save(outFilename);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
}
