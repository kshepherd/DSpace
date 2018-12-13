/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.statistics;

import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.commons.lang.StringUtils;
import org.dspace.app.statistics.Statistics;
import org.dspace.app.util.Util;
import org.dspace.app.xmlui.utils.HandleUtil;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.PageMeta;
import org.dspace.app.xmlui.wing.element.Select;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.statistics.content.DatasetTypeGenerator;
import org.dspace.statistics.content.StatisticsDataWorkflow;
import org.dspace.statistics.content.StatisticsDataWorkflowCounts;
import org.dspace.statistics.content.StatisticsTable;
import org.dspace.statistics.content.filter.StatisticsSolrDateFilter;
import org.elasticsearch.common.recycler.Recycler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.log4j.Logger;

/**
 * @author Kevin Van de Velde (kevin at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 */
public class StatisticsWorkflowTransformer extends AbstractStatisticsDataTransformer {

    private static final Message T_dspace_home = message("xmlui.general.dspace_home");
    private static final Message T_trail = message("xmlui.statistics.trail-workflow");
    private static final Message T_head_title = message("xmlui.statistics.workflow.title");
    private static final Message T_title = message("xmlui.statistics.workflow.title");
    private static final Message T_retrieval_error = message("xmlui.statistics.workflow.error");
    private static final Message T_no_results = message("xmlui.statistics.workflow.no-results");
    private static final Message T_workflow_head = message("xmlui.statistics.workflow.head");
    private static final Message T_workflow_head_dso = message("xmlui.statistics.workflow.head-dso");

    private static final Logger log = Logger.getLogger(StatisticsWorkflowTransformer.class);

    /**
     * Add a page title and trail links
     */
    public void addPageMeta(PageMeta pageMeta) throws SAXException, WingException, UIException, SQLException, IOException, AuthorizeException {
        //Try to find our dspace object
        DSpaceObject dso = HandleUtil.obtainHandle(objectModel);

        pageMeta.addTrailLink(contextPath + "/",T_dspace_home);

        if(dso != null)
        {
            HandleUtil.buildHandleTrail(dso, pageMeta, contextPath);
        }
        pageMeta.addTrailLink(contextPath + (dso != null && dso.getHandle() != null ? "/handle/" + dso.getHandle() : "") + "/workflow-statistics", T_trail);

        // Add the page title
        pageMeta.addMetadata("title").addContent(T_head_title);
    }


    @Override
    public void addBody(Body body) throws SAXException, WingException, SQLException, IOException, AuthorizeException, ProcessingException {
        //Try to find our dspace object
        DSpaceObject dso = HandleUtil.obtainHandle(objectModel);
        Request request = ObjectModelHelper.getRequest(objectModel);
        String selectedTimeFilter = request.getParameter("time_filter");


        StringBuilder actionPath = new StringBuilder().append(contextPath);
        if(dso != null){
            actionPath.append("/handle/").append(dso.getHandle());
        }
        actionPath.append("/workflow-statistics");

        Division mainDivision = body.addInteractiveDivision("workflow-statistics", actionPath.toString(), Division.METHOD_POST, null);
        if(dso != null){
            mainDivision.setHead(T_workflow_head_dso.parameterize(dso.getName()));
        }else{
            mainDivision.setHead(T_workflow_head);
        }
        try {
            // Get data
            StatisticsDataWorkflowCounts statisticsData = new StatisticsDataWorkflowCounts(dso);

            //Add the time filter box
            Date oldestWorkflowDate = statisticsData.getOldestWorkflowItemDate();
            Division workflowTermsDivision = mainDivision.addDivision("workflow-terms");
            workflowTermsDivision.setHead(T_title);
            addTimeFilter(workflowTermsDivision);

            //Retrieve the optional time filter
            StatisticsSolrDateFilter dateFilter = getDateFilter(selectedTimeFilter);

            addCalendarFilter(workflowTermsDivision, oldestWorkflowDate);

            int time_filter = -1;
            if(request.getParameter("time_filter") != null && !"".equals(request.getParameter("time_filter"))){
                //Our time filter is a negative value if present
                time_filter = Math.abs(Util.getIntParameter(request, "time_filter"));

            }

            StatisticsTable statisticsTable = new StatisticsTable(statisticsData);

            DatasetTypeGenerator queryGenerator = new DatasetTypeGenerator();
            //Set our type to previousworkflow step (indicates our performed actions !)
            queryGenerator.setType("previousWorkflowStep");
            queryGenerator.setMax(10);
            statisticsTable.addDatasetGenerator(queryGenerator);
            if(dateFilter != null){
                statisticsTable.addFilter(dateFilter);
            }
            addDisplayTable(workflowTermsDivision, statisticsTable, true, new String[]{"xmlui.statistics.display.table.workflow.step."});
        } catch (Exception e) {
            mainDivision.addPara().addContent(T_retrieval_error);
            log.info("Error generating workflow statistics", e);
        }
    }

    @Override
    protected Message getNoResultsMessage() {
        return T_no_results;
    }

    protected void addCalendarFilter(Division mainDivision, Date oldestDate) throws WingException, ParseException {

        if(oldestDate != null) {
            log.info("Oldest date is " + oldestDate.toGMTString());
        }
        else {
            log.info("Oldest date is null");
            oldestDate = new Date(2000,1,1);
        }

        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        Request request = ObjectModelHelper.getRequest(objectModel);
        String selectedTimeFilter = request.getParameter("calendar_filter");

        String selectedMonthFilter = request.getParameter("month_filter");
        String selectedYearFilter = request.getParameter("year_filter");

        Select monthFilter = mainDivision.addPara().addSelect("month_filter");
        /* old timefilter
        monthFilter.addOption(StringUtils.equals(selectedTimeFilter, "-1"), "-1", T_time_filter_last_month);
        monthFilter.addOption(StringUtils.equals(selectedTimeFilter, "-6"), "-6", T_time_filter_last6_months);
        monthFilter.addOption(StringUtils.equals(selectedTimeFilter, "-12"), "-12", T_time_filter_last_year);
        monthFilter.addOption(StringUtils.isBlank(selectedTimeFilter), "", T_time_filter_overall);
        */

        Calendar now = Calendar.getInstance();

        int currentMonth = now.get(Calendar.MONTH);
        for(int i = 0; i <= 11; i++) {
            SimpleDateFormat monthParse = new SimpleDateFormat("MM");
            SimpleDateFormat monthDisplay = new SimpleDateFormat("MMMM");
            boolean selected = isSelected(i,currentMonth,selectedMonthFilter);
            monthFilter.addOption(selected,i,monthDisplay.format(monthParse.parse(String.valueOf(i+1))));
        }
;
        int currentYear = now.get(Calendar.YEAR);
        int oldestYear = oldestDate.getYear();

        Select yearFilter = mainDivision.addPara().addSelect("year_filter");
        for(int i = oldestYear; i <= currentYear; i++) {
            boolean selected = isSelected(i, currentYear, selectedYearFilter);
            yearFilter.addOption(selected,String.valueOf(i),String.valueOf(i));
        }

        if(selectedYearFilter != null && selectedMonthFilter != null) {
            // generate start and end dates for solr
            int year = Integer.valueOf(selectedYearFilter)-1900;
            int month = Integer.valueOf(selectedMonthFilter)-1900;
            Date startDate = new Date(year,month,1);
            Date endDate = new Date(year,month,30);
            start.setTime(startDate);
            end.setTime(endDate);
            end.set(Calendar.DAY_OF_MONTH,end.getActualMaximum(Calendar.DAY_OF_MONTH));
            endDate = end.getTime();
            startDate = start.getTime();
            log.info("Start date : " + startDate.toGMTString() + ", End Date: " + endDate.toGMTString());
            StatisticsSolrDateFilter sdf = new StatisticsSolrDateFilter();
            sdf.setStartDate(startDate);
            sdf.setEndDate(endDate);
            log.info("The solrdatefilter query string is " + sdf.toQuery());
        }

    }

    private boolean isSelected(int i,int current,String filter) {
        boolean selected = false;
        if(filter == null) {
            if(current == i) {
                selected = true;
            }
        }
        else if(filter.equals(String.valueOf(i))) {
            selected = true;
        }
        return selected;
    }
}
