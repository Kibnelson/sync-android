/**
 * Copyright (c) 2013 Cloudant, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.cloudant.sync.indexing;

import com.cloudant.sync.datastore.DocumentBody;
import com.cloudant.sync.datastore.DocumentBodyFactory;
import com.cloudant.sync.datastore.BasicDocumentRevision;
import com.cloudant.sync.datastore.DatastoreExtended;
import com.cloudant.sync.datastore.DatastoreManager;
import com.cloudant.sync.datastore.MutableDocumentRevision;
import com.cloudant.sync.sqlite.SQLDatabase;
import com.cloudant.sync.util.TestUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class BasicQueryResultTest {

    SQLDatabase database = null;
    DatastoreExtended datastore = null;
    private String datastoreManagerPath;
    ExecutorService queue = null;

    @Before
    public void setUp() throws IOException, SQLException {
        datastoreManagerPath = TestUtils.createTempTestingDir(this.getClass().getName());
        DatastoreManager datastoreManager = new DatastoreManager(this.datastoreManagerPath);
        datastore = (DatastoreExtended) datastoreManager.openDatastore(getClass().getSimpleName());
        database = TestUtils.getDatabaseConnectionFromDatastore(datastore);
        queue = TestUtils.getDBQueue(datastore);

        for (int i = 0; i < 8; i++) {
            MutableDocumentRevision rev = new MutableDocumentRevision();
            rev.body = TestUtils.createBDBody("fixture/index" + "_" + i + ".json");
            datastore.createDocumentFromRevision(rev);
        }
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.deleteDatabaseQuietly(database);
        TestUtils.deleteTempTestingDir(datastoreManagerPath);
    }

    @Test
    public void size() {
        QueryResult res = new BasicQueryResult(getAllDBObjectIds(), datastore);
        Assert.assertEquals(8, res.size());
    }

    @Test
    public void iterator() {
        List<String> ids = getAllDBObjectIds();
        QueryResult res = new BasicQueryResult(ids, datastore);
        Iterator<BasicDocumentRevision> i = res.iterator();
        int count = 0;
        while(i.hasNext()) {
            BasicDocumentRevision obj = i.next();
            Assert.assertTrue(ids.contains(obj.getId()));
            count ++;
        }
        Assert.assertEquals(8, count);
    }

    @Test
    public void documentIds() {
        List<String> ids = getAllDBObjectIds();
        QueryResult res = new BasicQueryResult(ids, datastore);
        List<String> documentIds = res.documentIds();
        Assert.assertEquals(ids.size(), documentIds.size());
        for(int i = 0 ; i < ids.size() ; i ++) {
            BasicDocumentRevision o = datastore.getDocument(documentIds.get(i));
            Assert.assertTrue(ids.get(i).equals(o.getId()));
        }
    }

    private List<String> getAllDBObjectIds() {
        List<BasicDocumentRevision> data = datastore.getAllDocuments(0, 100, true);
        List<String> ids = new ArrayList<String>();
        for(BasicDocumentRevision obj : data) {
            ids.add(obj.getId());
        }
        return ids;
    }

    @Test
    public void iterable_queryResultHas8DocumentsAndDefaultBatchSize_allItemsAreInResult() {
        List<String> ids = getAllDBObjectIds();
        QueryResult res = new BasicQueryResult(ids, datastore);
        this.assertQueryResult(res, ids);
    }

    @Test
    public void iterable_queryResultHas8DocumentsAndBatchSizeIs4_iterationWorks() {
        List<String> ids = this.getAllDBObjectIds();
        QueryResult res = new BasicQueryResult(ids, datastore, 4);
        this.assertQueryResult(res, ids);
    }

    @Test
    public void iterable_emptyResults_iterationWorks() {
        List<String> ids = new ArrayList<String>();
        QueryResult res = new BasicQueryResult(ids, datastore);
        Assert.assertFalse(res.iterator().hasNext());
        int count = 0;
        for(BasicDocumentRevision ignored : res) {
            count ++;
        }
        Assert.assertEquals(0, count);
    }

    @Test
    public void iterable_documentNotExistAnyMore_exception() {
        List<String> ids = this.getAllDBObjectIds();
        ids.add("badId");
        QueryResult res = new BasicQueryResult(ids, datastore);
        List<String> expected = new ArrayList<String>(ids);
        expected.remove("badId");
        this.assertQueryResult(res, expected);
    }

    @Test
    public void iterable_queryResultHas51DocumentsAndUseDefaultBatchSize50_iterationWorks() throws IOException {
        testQueryResultBatchWithSize(51);
    }

    @Test
    public void iterable_queryResultHas1KDocumentsAndUseDefaultBatchSize50_iterationWorks() throws IOException {
        testQueryResultBatchWithSize(1000);
    }

    private void testQueryResultBatchWithSize(int queryResultSize) throws IOException {
        List<String> ids = new ArrayList<String>(queryResultSize);
        for(int i = 0 ; i < queryResultSize ; i ++) {
            Map m = new HashMap<String, String>();
            m.put("name", "Tom");
            m.put("tag", "tag" + i);
            DocumentBody body = DocumentBodyFactory.create(m);
            MutableDocumentRevision rev = new MutableDocumentRevision();
            rev.body = body;
            BasicDocumentRevision doc = datastore.createDocumentFromRevision(rev);
            ids.add(doc.getId());
        }
        QueryResult qr = new BasicQueryResult(ids, datastore);
        Assert.assertEquals(queryResultSize, ids.size());
        this.assertQueryResult(qr, ids);
    }

    private void assertQueryResult(QueryResult qr, List<String> ids) {
        List<String> expected = new ArrayList<String>(ids);
        for(BasicDocumentRevision rev : qr) {
            Assert.assertEquals(expected.remove(0), rev.getId());
        }
        Assert.assertTrue(expected.size() == 0);
    }
}
