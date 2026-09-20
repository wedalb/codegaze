package io.codegaze.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MappingTest {
    static Model.Target target(String text, String revision) {
        return new Model.Target("IDENTIFIER",text,"Example.java",revision,10,10+text.length(),2,4,
                "Example.java:5:value","local_variable",List.of(new Model.Rect(100,50,80,20)));
    }
    static Model.Frame frame(long id, String text, String revision) {
        return new Model.Frame(id,1000,1000000,1000,500,"jpeg","Example.java","ready",List.of(target(text,revision)));
    }
    static Model.Sample sample(long sequence,long frame,Double u,Double v,String source,boolean valid) {
        return new Model.Sample("test",sequence,frame,sequence*33.0,1000.0,source,null,valid,u,v,
                List.of(0.0,0.0,0.0),List.of(0.0,0.0,-1.0),null,"unavailable");
    }
    @Test void usesDisplayedRevisionAfterCodeChanges() {
        FrameStore store=new FrameStore(2);store.add(frame(1,"before","rev1"));store.add(frame(2,"after","rev2"));
        Model.Target hit=Mapper.map(store.get(1),sample(0,1,.12,.12,"eye",true)).target();
        assertEquals("before",hit.text());assertEquals("rev1",hit.revision());
    }
    @Test void expiresFramesWithoutGuessingCurrentCode() {
        FrameStore store=new FrameStore(1);store.add(frame(1,"before","rev1"));store.add(frame(2,"after","rev2"));
        assertEquals("frame_expired",Mapper.map(store.get(1),sample(1,1,.12,.12,"head",true)).status());
    }
    @Test void usesHalfOpenBoundsAndKeepsWhitespaceUnmapped() {
        Model.Frame f=frame(1,"value","rev");
        assertEquals("mapped",Mapper.map(f,sample(1,1,.1,.1,"head",true)).status());
        assertEquals("no_token",Mapper.map(f,sample(2,1,.18,.1,"head",true)).status());
        assertEquals("off_screen",Mapper.map(f,sample(3,1,1.0,.1,"head",true)).status());
        assertEquals("off_screen",Mapper.map(f,sample(4,1,null,null,"head",true)).status());
    }
    @Test void neverMapsLostTracking() {
        assertEquals("tracking_lost",Mapper.map(frame(1,"value","r"),sample(1,1,.12,.12,"eye",false)).status());
    }
    @Test void rejectsNonFiniteCoordinatesAndUnlabelledSources() {
        assertThrows(IllegalArgumentException.class,()->Mapper.validate(sample(1,1,Double.NaN,.1,"eye",true)));
        assertThrows(IllegalArgumentException.class,()->Mapper.validate(sample(1,1,.1,.1,"gaze",true)));
    }
    @Test void overlappingTargetsAreExplicitlyAmbiguous() {
        Model.Frame f=frame(1,"value","r");
        Model.Frame overlap=new Model.Frame(1,1000,1,1000,500,"jpeg","Example.java","ready",List.of(target("one","r"),target("two","r")));
        assertEquals("ambiguous",Mapper.map(overlap,sample(1,1,.12,.12,"eye",true)).status());
    }
    @Test void csvEscapesQuotesCommasNewlinesAndSpreadsheetFormulas() {
        assertEquals("\"'={cmd}\"",Csv.cell("={cmd}"));
        assertEquals("\"a,\"\"b\"\"\nc\"",Csv.cell("a,\"b\"\nc"));
    }
}
