package folioxml.lucene;

import folioxml.config.InfobaseConfig;
import folioxml.config.InfobaseSet;
import folioxml.core.InvalidMarkupException;
import folioxml.core.TokenUtils;
import folioxml.export.InfobaseSetPlugin;
import folioxml.lucene.analysis.AnalyzerPicker;
import folioxml.lucene.analysis.DynamicAnalyzer;
import folioxml.slx.ISlxTokenReader;
import folioxml.slx.SlxContextStack;
import folioxml.slx.SlxRecord;
import folioxml.slx.SlxToken;
import folioxml.xml.XmlRecord;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class InfobaseSetIndexer implements InfobaseSetPlugin, AnalyzerPicker{


    public InfobaseSetIndexer(){

    }

    InfobaseFieldOptsSet conf;
    IndexWriter w;

    @Override
    public void beginInfobaseSet(InfobaseSet set, String exportBaseName) throws IOException {
        w = new IndexWriter(FSDirectory.open(new File(set.getIndexDir())), new IndexWriterConfig(Version.LUCENE_33, new DynamicAnalyzer(this)).setOpenMode(IndexWriterConfig.OpenMode.CREATE));

    }

    @Override
    public void beginInfobase(InfobaseConfig infobase) {
        conf = null;
        currentInfobase = infobase;
    }

    InfobaseConfig currentInfobase;
    @Override
    public ISlxTokenReader wrapSlxReader(ISlxTokenReader reader) {
        return reader;
    }

    Document doc = null;
    @Override
    public void onSlxRecordParsed(SlxRecord r) throws InvalidMarkupException {
        boolean isRoot = r.isRootRecord();

        //Create lucene document, add some fields.
        doc = new Document();
        //Add level, groups, infobase
        if (r.getLevelType() == null) {
            doc.add(addNonAnalyzedField("level", "Normal"));
        } else {
            doc.add(addNonAnalyzedField("level", r.getLevelType()));
        }
        doc.add(addAnalyzedField("groups", r.get("groups")));
        doc.add(addNonAnalyzedField("infobase", currentInfobase.getId()));

        if (!isRoot){
            //Iterate all tokens and stream to applicable fields so a query can be evaluated later
            FieldCollector coll = new FieldCollector(doc, conf);

            StringBuilder contentSb = new StringBuilder();
            SlxContextStack stack = new SlxContextStack(false,false);
            List<String> destinations = new ArrayList<String>();
            stack.process(r);
            for (SlxToken t : r.getTokens()) {
                stack.process(t);// call this on each token.

                boolean hidden = coll.collect(t, stack,r);

                if (!hidden && t.isTextOrEntity()) { //Changed dec 17 to include whitespace... was causing indexing errors.. fields separated by whitespace were being joined.
                    String s = t.markup;
                    if (t.isEntity()) s = TokenUtils.entityDecodeString(s);
                    contentSb.append(s);
                }
                if (t.matches("p|br|td|th|note") && !t.isOpening()) {
                    contentSb.append(" ");contentSb.append(TokenUtils.entityDecodeString("&#x00A0;")); contentSb.append(" ");
                }
                if (t.isTag() && t.matches("bookmark")){
                    //Add bookmarks as-is
                    doc.add(new Field("destinations", t.get("name"), Field.Store.NO, Field.Index.NOT_ANALYZED));
                }
            }

            doc.add(new Field(conf.getDefaultField(),contentSb.toString(), Field.Store.YES,Field.Index.ANALYZED, Field.TermVector.NO));


            doc.add(new Field("title",r.getFullHeading(" - ",true,2),Field.Store.YES, Field.Index.NO));
            doc.add(new Field("heading",r.get("heading"),Field.Store.YES, Field.Index.NO));

            coll.flush();
        }
    }

    @Override
    public void onRecordTransformed(SlxRecord dirty_slx, XmlRecord r) throws InvalidMarkupException, IOException {
        //Add URI
        if (r.get("uri") != null) doc.add(new Field("uri",r.get("uri"),Field.Store.YES, Field.Index.NOT_ANALYZED));

        if (dirty_slx.isRootRecord()){
            //Configure field indexing based on the .DEF file.
            conf = new InfobaseFieldOptsSet(r);
            doc.add(new Field("xml",r.toXmlString(false),Field.Store.YES, Field.Index.NO));
        }

        w.addDocument(doc);
    }

    @Override
    public void endInfobase(InfobaseConfig infobase) {

    }

    @Override
    public void endInfobaseSet(InfobaseSet set) throws IOException {
        try{
            w.optimize();
        }finally{
            w.close();
        }
    }



    private Field addNonAnalyzedField(String name, String value) {
        return new Field(name, value.toLowerCase(Locale.ENGLISH).trim(), Field.Store.YES,
                Field.Index.NOT_ANALYZED_NO_NORMS);
    }
    private Field addNonAnalyzedNoStoreField(String name, String value) {
        return new Field(name, value.toLowerCase(Locale.ENGLISH).trim(),
                Field.Store.NO, Field.Index.NOT_ANALYZED);
    }


    private Field addAnalyzedField(String name, String value) {
        if  (value == null) value = ""; //Some records have no groups... causing null
        return new Field(name, value, Field.Store.YES, Field.Index.ANALYZED);
    }

    @Override
    public Analyzer getAnalyzer(String fieldName) {
        return conf.getAnalyzer(fieldName);
    }
}