package com.datastax.mgmtapi;

import com.datastax.mgmtapi.resources.models.FeatureSet;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpStatus;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.Assert;
import org.junit.Test;

import java.net.URISyntaxException;

import static com.datastax.mgmtapi.K8OperatorResourcesTest.setup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class MetadataResourcesTest {

    @Test
    public void testGetReleaseVersion() throws Exception {
        K8OperatorResourcesTest.Context context = setup();
        MockHttpResponse response = getMockHttpResponse(context, "/metadata/versions/release");
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        Assert.assertTrue(response.getContentAsString().contains("1.2.3"));
    }

    @Test
    public void testFeatureSetEndpoint() throws Exception {
        K8OperatorResourcesTest.Context context = setup();
        MockHttpResponse response = getMockHttpResponse(context, "/metadata/versions/features");
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        String json = response.getContentAsString();
        FeatureSet featureSet = new ObjectMapper().readValue(json, FeatureSet.class);

        assertEquals("1.2.3", featureSet.getCassandraVersion());
        assertEquals("", featureSet.getMgmtVersion());
        assertTrue(featureSet.getFeatures().size() > 0);
    }

    private MockHttpResponse getMockHttpResponse(K8OperatorResourcesTest.Context context, String path) throws URISyntaxException, ConnectionClosedException {
        ResultSet mockResultSet = mock(ResultSet.class);
        Row mockRow = mock(Row.class);

        MockHttpRequest request = MockHttpRequest.get(K8OperatorResourcesTest.ROOT_PATH + path);
        when(context.cqlService.executeCql(any(), anyString()))
                .thenReturn(mockResultSet);

        when(mockResultSet.one())
                .thenReturn(mockRow);

        when(mockRow.getString(0))
                .thenReturn("1.2.3");

        MockHttpResponse response = context.invoke(request);

        assertEquals(HttpStatus.SC_OK, response.getStatus());
        verify(context.cqlService).executeCql(any(), eq("CALL NodeOps.getReleaseVersion()"));
        return response;
    }
}
