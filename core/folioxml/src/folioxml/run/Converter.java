package folioxml.run;

import folioxml.core.InvalidMarkupException;
import folioxml.directexport.DirectXhtmlExporter;
import folioxml.directexport.DirectXmlExporter;
import folioxml.slx.SlxRecordReader;

import java.io.File;
import java.io.IOException;

public class Converter {
    public static void main(String[] args) throws InvalidMarkupException, IOException {
        //failed: pg_catch
        String names[] = {
//                "pg_catch",
//                "pg_cases",
//                "pg_citor",
//                "pg_leg_h",
//                "pg_leg_i",
//                "pg_leg_n",
                "pg_leg_p",
                "pg_leg_r",
                "pg_leg_s",
                "pg_leg_sess",
                "pg_texts",
                "pg_treat",
                "pg_updat",
        };

        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            String sourceFile = "C:\\Users\\Tindase\\Desktop\\fff\\" + name + ".FFF";
            System.out.println();
            System.out.println("handling: " + sourceFile.toUpperCase());
            File f = new File(sourceFile);
            SlxRecordReader srr = new SlxRecordReader(f);
            srr.silent = false;
            String xhtmlFile = f.getParent() + File.separatorChar + f.getName()+ ".xhtml";
            String xmlFile = f.getParent() + File.separatorChar + f.getName() + ".xml";

            DirectXhtmlExporter xh = new DirectXhtmlExporter(srr,xhtmlFile);
//            DirectXmlExporter xh = new DirectXmlExporter(srr, xmlFile);
            xh.processAll();
            xh.close();
            srr.close();
            System.out.println("--0--0--");
        }
    }
}
