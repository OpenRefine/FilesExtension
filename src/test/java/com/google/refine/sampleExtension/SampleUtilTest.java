package com.google.refine.sampleExtension;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

import java.io.Serializable;

import org.openrefine.extensions.files.FilesUtil;
import org.testng.annotations.Test;
import com.google.refine.RefineTest;
import com.google.refine.model.Project;

public class SampleUtilTest extends RefineTest {
    
    @Test
    public void testStringArrayLength() {
        // you can use the same sort of test utilities as in OpenRefine's own test suite
        // The project created below is unused here, but shows you how you could create one for your tests.
        Project project = createProject(new String[] { "first column", "second column" },
                new Serializable[][] {
                  { "a", "b" },
                  { "c", 3 }
        });

        String[] myArray = new String[] { "foo", "bar" };
        
        assertEquals(FilesUtil.stringArrayLength(myArray), 2);
    }
    
    @Test
    public void testNullPointerException() {
        assertThrows(NullPointerException.class, () -> FilesUtil.stringArrayLength(null));
    }

}
