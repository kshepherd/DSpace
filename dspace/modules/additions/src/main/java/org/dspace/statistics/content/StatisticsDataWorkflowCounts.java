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
        actions.put("submitted","previousWorkflowStep:SUBMIT");
        actions.put("approved","workflowStep:STEP3POOL AND previousWorkflowStep:STEP2");
        actions.put("finalcheck","workflowStep:ARCHIVE AND previousWorkflowStep:STEP3");
        actions.put("rejected","workflowStep:SUBMIT AND (previousWorkflowStep:STEP2 OR previousWorkflowStep:STEP3)");

        List<String> facetQueries = new ArrayList<>();
        facetQueries.add(actions.get("submitted")); // submitted items
        facetQueries.add(actions.get("approved")); // approved and sent to final check
        facetQueries.add(actions.get("finalcheck")); // approved and archived from final
        facetQueries.add(actions.get("rejected")); // rejected from either step

        Dataset dataset = new Dataset(0,0);
        List<DatasetGenerator> datasetGenerators = getDatasetGenerators();
        if(0 < datasetGenerators.size()){
            //At the moment we can only have one dataset generator
            DatasetGenerator datasetGenerator = datasetGenerators.get(0);
            if(datasetGenerator instanceof DatasetTypeGenerator){
                DatasetTypeGenerator typeGenerator = (DatasetTypeGenerator) datasetGenerator;
                Map<String, Integer> objectCounts = SolrLogger.queryFacetQuery("statistics_type:workflow",
                        defaultFilterQuery, facetQueries, typeGenerator.getMax(),0);
                for (String o : objectCounts.keySet()) {
                    log.info(o + ": " + objectCounts.get(o));
                }

                dataset = new Dataset(objectCounts.size(), 2);
                dataset.setColLabel(0, "action");
                dataset.setColLabel(1, "count");

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
        String query = "statistics_type:" + SolrLogger.StatisticsType.WORKFLOW.text();;
        if(currentDso != null){
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

    public Date getOldestWorkflowItemDate() throws SolrServerException {
        ConfigurationService configurationService = new DSpace().getConfigurationService();
        String workflowStartDate = configurationService.getProperty("usage-statistics.workflow-start-date");
        if(workflowStartDate == null){
            //Query our solr for it !
            QueryResponse oldestRecord = SolrLogger.query(getQuery(), null, null, 1, 0, null, null, null, null, "time", true);
            if(0 < oldestRecord.getResults().getNumFound()){
                SolrDocument solrDocument = oldestRecord.getResults().get(0);
                Date oldestDate = (Date) solrDocument.getFieldValue("time");
                //Store the date, we only need to retrieve this once !
                try {
                    //Also store it in the solr-statics configuration file, the reason for this being that the sort query
                    //can be very time consuming & we do not want this delay each time we want to see workflow statistics
                    String solrConfigDir = configurationService.getProperty("dspace.dir") + File.separator + "config"
                            + File.separator + "modules" + File.separator + "usage-statistics.cfg";
                    PropertiesConfiguration config = new PropertiesConfiguration(solrConfigDir);
                    config.setProperty("workflow-start-date", new DCDate(oldestDate));
                    config.save();
                } catch (ConfigurationException e) {
                    log.error("Error while storing workflow start date", e);
                }
                //ALso store it in our local config !
                configurationService.setProperty("usage-statistics.workflow-start-date", new DCDate(oldestDate).toString());

                //Write to file
                return oldestDate;
            }else{
                return null;
            }

        }else{
            return new DCDate(workflowStartDate).toDate();
        }
    }
}
