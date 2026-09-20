package io.codegaze.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class RecorderTest {
    @TempDir Path directory;
    @Test void fullSessionPersistsRawSamplesAndFrameBoundTargets() throws Exception {
        try(Recorder recorder=new Recorder(directory)) {
            recorder.start(Map.of("participant","P01"));
            Model.Event eye=recorder.record(MappingTest.sample(1,1,.12,.12,"eye",true),MappingTest.frame(1,"quantity","rev1"));
            Model.Event head=recorder.record(MappingTest.sample(2,1,.12,.12,"head",true),MappingTest.frame(1,"quantity","rev1"));
            assertEquals("eye",eye.sample().source());assertEquals("head",head.sample().source());
            assertEquals("quantity",eye.target().text());
            assertThrows(IllegalArgumentException.class,()->recorder.record(MappingTest.sample(2,1,.12,.12,"head",true),MappingTest.frame(1,"q","r")));
            assertThrows(IllegalStateException.class,recorder::export);
            recorder.stop();
            Map<String,String> files=new HashMap<>();
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(recorder.export()))) {
                ZipEntry entry;while((entry=zip.getNextEntry())!=null)files.put(entry.getName(),new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            }
            assertEquals(Set.of("session.json","samples.jsonl","samples.csv","frames.jsonl"),files.keySet());
            assertEquals(2,files.get("samples.jsonl").lines().count());
            assertEquals(1,files.get("frames.jsonl").lines().count());
            assertTrue(files.get("samples.csv").contains("quantity"));
            assertTrue(files.get("frames.jsonl").contains("\"image\":null"));
        }
    }
    @Test void noRecordingCannotSilentlyAcceptSamples() {
        Recorder recorder=new Recorder(directory);
        assertThrows(IllegalStateException.class,()->recorder.record(MappingTest.sample(0,1,.1,.1,"eye",true),null));
    }
}
