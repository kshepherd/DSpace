/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.statistics.content;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.dspace.content.DCDate;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.statistics.Dataset;
import org.dspace.statistics.ObjectCount;
import org.dspace.statistics.SolrLogger;
import org.dspace.statistics.content.filter.StatisticsFilter;
import org.dspace.statistics.content.filter.StatisticsSolrDateFilter;
import org.dspace.utils.DSpace;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

/**
 * A workflow data implementation that will query the statistics backend for workflow information
 *
 * @author Kevin Van de Velde (kevin at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 */
public class StatisticsDataWorkflowCounts extends StatisticsData {

    private static final Logger log = Logger.getLogger(StatisticsDataWorkflowCounts.class);

    /** Current DSpaceObject for which to generate the statistics. */
    private DSpaceObject currentDso;

    public StatisticsDataWorkflowCounts(DSpaceObject dso) {
        super();
        this.currentDso = dso;
    }


    @Override
    public Dataset createDataset(Context context) throws SQLException, SolrServerException, IOException, ParseException {
        // Check if we already have one.
        // If we do then give it back.
        if(getDataset() != null)
        {
            return getDataset();
        }

        List<StatisticsFilter> filters = getFilters();
        List<String> defaultFilters = new ArrayList<String>();
        for (StatisticsFilter statisticsFilter : filters) {
            defaultFilters.add(statisticsFilter.toQuery());
        }

        String defaultFilterQuery = StringUtils.join(defaultFilters.iterator(), " AND ");

        String query = getQuery();

        Map<String,String> actions = new HashMap<>();
        actions.put("Submitted to workflow","previousWorkflowStep:SUBMIT");
        actions.put("Approved and sent to final check","workflowStep:STEP3POOL AND previousWorkflowStep:STEP2");
        actions.put("Approved and archived from final check","workflowStep:ARCHIVE AND previousWorkflowStep:STEP3");
        actions.put("Rejected from any workflow step","workflowStep:ARCHIVE AND previousWorkflowStep:STEP3");

        List<String> facetQueries = new ArrayList<>();
        facetQueries.add(actions.get("Submitted to workflow")); // submitted items
        facetQueries.add(actions.get("Approved and sent to final check")); // approved and sent to final check
        facetQueries.add(actions.get("Approved and archived from final check")); // approved and archived from final
        facetQueries.add(actions.get("Rejected from any workflow step")); // rejected from either step

        Dataset dataset = new Dataset(0,0);
        List<DatasetGenerator> datasetGenerators = getDatasetGenerators();
        if(0 < datasetGenerators.size()){
            //At the moment we can only have one dataset generator
            DatasetGenerator datasetGenerator = datasetGenerators.get(0);
            if(datasetGenerator instanceof DatasetTypeGenerator){
                DatasetTypeGenerator typeGenerator = (DatasetTypeGenerator) datasetGenerator;
                Map<String, Integer> objectCounts = SolrLogger.queryFacetQuery(query, defaultFilterQuery, facetQueries, typeGenerator.getMax());

                dataset = new Dataset(objectCounts.size(), 2);
                dataset.setColLabel(0, "Workflow Action");
                dataset.setColLabel(1, "Total Count");

                int i = 0;
                for (String action : actions.keySet()) {
                    long count = 0;
                    if(objectCounts.containsKey(actions.get(action))) {
                        count = objectCounts.get(actions.get(action));
                    }
                    dataset.setRowLabel(i, String.valueOf(i + 1));
                    dataset.addValueToMatrix(i, 0, action);
                    dataset.addValueToMatrix(i, 1, count);
                    i++;
                }

            }
        }

        return dataset;
    }

    /**
     * Returns the query to be used in solr
     * in case of a dso a scopeDso query will be returned otherwise the default *:* query will be used
     * @return the query as a string
     */
    protected String getQuery() {
        String query = "";
        if(currentDso != null){
            query += "statistics_type:" + SolrLogger.StatisticsType.WORKFLOW.text();
            if(currentDso.getType() == Constants.COMMUNITY){
                query += " AND owningComm:";

            }else
            if(currentDso.getType() == Constants.COLLECTION){
                query += " AND owningColl:";
            }
            query += currentDso.getID();
        }
        return query;
    }

    /**
     * Retrieve the total counts for the facets (total count is same query but none of the filter queries
     * @param typeGenerator the type generator
     * @return as a key the
     * @throws SolrServerException
     */
    protected Map<String, Long> getTotalFacetCounts(DatasetTypeGenerator typeGenerator) throws SolrServerException {
        ObjectCount[] objectCounts = SolrLogger.queryFacetField(getQuery(), null, typeGenerator.getType(), -1, false, null);
        Map<String, Long> result = new HashMap<String, Long>();
        for (ObjectCount objectCount : objectCounts) {
            result.put(objectCount.getValue(), objectCount.getCount());
        }
        return result;
    }
}
