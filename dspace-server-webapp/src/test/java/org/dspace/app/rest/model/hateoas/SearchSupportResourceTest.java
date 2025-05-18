/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model.hateoas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.dspace.app.rest.model.SearchSupportRest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This class' purpose is to test the SearchSupportResource class
 */
public class SearchSupportResourceTest {
    private SearchSupportRest searchSupportRest;

    @Before
    public void setUp() throws Exception {
        searchSupportRest = new SearchSupportRest();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullThrowsException() throws Exception {
        SearchSupportResource searchSupportResource = new SearchSupportResource(null);
    }

    @Test
    public void testConstructorAndGetterWithProperDataAndObjectNotNull() throws Exception {
        SearchSupportResource searchSupportResource = new SearchSupportResource(searchSupportRest);
        assertNotNull(searchSupportResource);
        assertNotNull(searchSupportResource.getContent());
    }

    @Test
    public void testConstructorAndGetterWithProperDataAndProperDataReturned() throws Exception {
        SearchSupportResource searchSupportResource = new SearchSupportResource(searchSupportRest);
        assertEquals(searchSupportRest, searchSupportResource.getContent());
    }
}
