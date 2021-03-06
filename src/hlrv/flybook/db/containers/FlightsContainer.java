package hlrv.flybook.db.containers;

import hlrv.flybook.FlightType;
import hlrv.flybook.FlybookUI;
import hlrv.flybook.auth.User;
import hlrv.flybook.db.DBConnection;
import hlrv.flybook.db.DBConstants;
import hlrv.flybook.db.items.FlightItem;

import java.sql.SQLException;
import java.util.Date;

import com.vaadin.data.Container.Filter;
import com.vaadin.data.Item;
import com.vaadin.data.util.IndexedContainer;
import com.vaadin.data.util.filter.And;
import com.vaadin.data.util.filter.Compare;
import com.vaadin.data.util.filter.Compare.Equal;
import com.vaadin.data.util.sqlcontainer.RowId;
import com.vaadin.data.util.sqlcontainer.SQLContainer;
import com.vaadin.data.util.sqlcontainer.connection.JDBCConnectionPool;
import com.vaadin.data.util.sqlcontainer.query.TableQuery;
import com.vaadin.ui.UI;

/**
 * FlightsContainer abstracts SQLContainer to table "FlightEntries".
 */
public class FlightsContainer {

    /**
     * Primary container.
     */
    private SQLContainer flightsContainer;

    /**
     * Keep reference to filters so we can remove/add them from container.
     */
    private Filter usernameFilter;
    private Filter dateFilter;
    private Filter flightTypeFilter;

    /**
     * Container that holds flight types.
     */
    private IndexedContainer flightTypesContainer;

    public static final String PID_FLIGHT_TYPE = "type";

    /**
     * Create new instance of FlightsContainer that uses DBConnection given as
     * argument.
     */
    public FlightsContainer(DBConnection dbconn) throws SQLException {

        JDBCConnectionPool pool = dbconn.getPool();

        TableQuery query = new TableQuery(DBConstants.TABLE_FLIGHTENTRIES, pool);
        query.setVersionColumn(DBConstants.FLIGHTENTRIES_OPTLOCK);

        // FreeformQuery query = new
        // FreeformQuery("SELECT * FROM FlightEntries",
        // pool, DBConstants.FLIGHTENTRIES_FLIGHT_ID);

        // FlightEntriesFSDeletegate delegate = new FlightEntriesFSDeletegate();

        // query.setDelegate(delegate);

        flightsContainer = new SQLContainer(query);
        flightsContainer.setAutoCommit(false);

        flightTypesContainer = createFlightTypesContainer();

        // container.setPageLength(5 * container.size());
    }

    /**
     * Returns the SQLContainer.
     */
    public SQLContainer getContainer() {
        return flightsContainer;
    }

    /**
     * Returns flight types container.
     */
    public IndexedContainer getFlightTypesContainer() {
        return flightTypesContainer;
    }

    public boolean containsItem(Integer flightId) {

        Object[] pkey = { flightId };
        RowId id = new RowId(pkey);
        return flightsContainer.getItemUnfiltered(id) != null;
    }

    /**
     * Add username filter. If null, removes filter.
     */
    public void filterByUser(String username) {

        if (usernameFilter != null) {
            flightsContainer.removeContainerFilter(usernameFilter);
            usernameFilter = null;
        }

        if (username != null) {
            /**
             * Must use table prefix for filter to work with custom implemented
             * FreeformQuery (NOTE: not used presently). Also requires some
             * trickery in FlightEntriesFSDelegate constructor (something to do
             * with quotes)
             */
            // usernameFilter = new Equal("FlightEntries.username", username);
            usernameFilter = new Equal(DBConstants.FLIGHTENTRIES_USERNAME,
                    username);
            flightsContainer.addContainerFilter(usernameFilter);
        }
    }

    /**
     * Add date filter. If either argument is null, removes filter.
     * 
     * Filters out all flights not overlapping given time range.
     */
    public void filterByDate(Integer timeFrom, Integer timeTo) {

        if (timeFrom > timeTo) {
            throw new IllegalArgumentException(
                    "Filter timeFrom must be greater or equal to timeTo");
        }

        if (dateFilter != null) {
            flightsContainer.removeContainerFilter(dateFilter);
            dateFilter = null;
        }

        /**
         * Filter: (departure <= to) && (landing >= from)
         */
        if (timeFrom != null && timeTo != null) {
            Filter ge = new Compare.LessOrEqual(
                    DBConstants.FLIGHTENTRIES_DEPARTURE_TIME, timeTo);
            Filter le = new Compare.GreaterOrEqual(
                    DBConstants.FLIGHTENTRIES_LANDING_TIME, timeFrom);

            dateFilter = new And(le, ge);
            flightsContainer.addContainerFilter(dateFilter);
        }
    }

    /**
     * Add flighttype filter. If null, removes filter.
     */
    public void filterByFlightType(Integer type) {

        if (flightTypeFilter != null) {
            flightsContainer.removeContainerFilter(flightTypeFilter);
            flightTypeFilter = null;
        }

        if (type != null) {

            flightTypeFilter = new Equal(DBConstants.FLIGHTENTRIES_FLIGHT_TYPE,
                    type);
            flightsContainer.addContainerFilter(flightTypeFilter);
        }
    }

    /**
     * Creates a new row in container and initializes it with default values.
     * 
     * Note that new row is temporary only and commit() must be called in order
     * to finalize addition. Temporary row addition can be cancelled by calling
     * rollback() instead.
     * 
     * @return FlightItem
     */
    public FlightItem addEntry() {

        Object tempId = flightsContainer.addItem(); // returns temporary row id

        /**
         * getItem() ignores filtered objects, so must use this one.
         */
        FlightItem flightItem = new FlightItem(
                flightsContainer.getItemUnfiltered(tempId), tempId);

        /**
         * Initialize item with some sane values.
         */
        User curUser = ((FlybookUI) UI.getCurrent()).getUser().getBean();

        Date curTime = new Date();
        Integer curTimeSecs = (int) (curTime.getTime() / 1000L);

        /**
         * FlightID is special case, it should probably be null, so that when
         * commit is called, database can initialize id with unique value.
         * 
         * NOTE: There is annoying "feature" in SQLContainer when source table
         * has 0 rows. SQLContainer propertyTypes are set to Object, which
         * causes nullpointer error in SQLContainer row insertion code. For now,
         * create id manually.
         */
        flightItem.setFlightID(getUniqueIndex());

        flightItem.setDate(curTimeSecs);
        flightItem.setUsername(curUser.getUsername());
        // flightItem.setPilotFullname(curUser.getFirstname() + " "
        // + curUser.getLastname());

        // TODO: Could be neat if new item was initialized to users current
        // physical location...
        flightItem.setDepartureAirport(null);
        flightItem.setDepartureTime(curTimeSecs);

        flightItem.setLandingAirport(null);
        flightItem.setLandingTime(curTimeSecs);

        flightItem.setAircraft(null);

        flightItem.setOnBlockTime(0);
        flightItem.setOffBlockTime(0);

        flightItem.setFlightType(0);

        flightItem.setIFRTime(0);

        flightItem.setNotes("");

        return flightItem;
    }

    /**
     * Removes item from container.
     * 
     * @param item
     * @return true if entry successfully removed
     */
    public boolean removeEntry(FlightItem item) {

        // Object[] pkey = { new Integer(item.getFlightID()) };
        // RowId id = new RowId(pkey);

        return flightsContainer.removeItem(item.getItemId());
    }

    /**
     * Commit changes to SQLContainer.
     */
    public void commit() throws SQLException {

        flightsContainer.commit();
    }

    /**
     * Rollback changes.
     */
    public void rollback() throws SQLException {

        flightsContainer.rollback();
    }

    private IndexedContainer createFlightTypesContainer() {

        final String caption = PID_FLIGHT_TYPE;

        IndexedContainer flightTypeContainer = new IndexedContainer();
        flightTypeContainer.addContainerProperty(caption, String.class, null);

        for (FlightType type : FlightType.values()) {
            Item item = flightTypeContainer
                    .addItem((new Integer(type.ordinal())));
            item.getItemProperty(caption).setValue(type.getName());
        }

        return flightTypeContainer;
    }

    private int getUniqueIndex() {
        int flightId = 1;
        while (true) {
            if (!containsItem(flightId)) {
                return flightId;
            }
            ++flightId;
        }
    }

}
