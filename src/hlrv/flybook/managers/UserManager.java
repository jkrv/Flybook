package hlrv.flybook.managers;

import hlrv.flybook.auth.Hash;
import hlrv.flybook.auth.User;
import hlrv.flybook.db.DBConstants;

import java.sql.SQLException;

import com.vaadin.data.Item;
import com.vaadin.data.util.filter.Compare.Equal;
import com.vaadin.data.util.sqlcontainer.SQLContainer;
import com.vaadin.data.util.sqlcontainer.connection.JDBCConnectionPool;
import com.vaadin.data.util.sqlcontainer.query.TableQuery;

public class UserManager {
    private final SQLContainer container;
    private final TableQuery tq;

    public UserManager(JDBCConnectionPool pool) throws SQLException {
        tq = new TableQuery("users", pool);
        tq.setVersionColumn("optlock");
        this.container = new SQLContainer(tq);
    }

    private Item getItemFromUsername(String username) throws Exception {
        this.container.removeAllContainerFilters();
        this.container.addContainerFilter(new Equal("username", username));
        Object id = this.container.firstItemId();
        this.container.removeAllContainerFilters();
        if (id == null) {
            throw new Exception("User not found");
        }
        // TODO:
        // System.err.println(this.container.getItem(id).getItemPropertyIds()
        // .toString());
        return this.container.getItem(id);
    }

    public User getFromUsername(String username) throws Exception {
        Item item = this.getItemFromUsername(username);
        String firstname = (String) item.getItemProperty("firstname")
                .getValue();
        String lastname = (String) item.getItemProperty("lastname").getValue();
        String email = (String) item.getItemProperty("email").getValue();
        Integer admin = (Integer) item.getItemProperty("admin").getValue();
        return new User(username, firstname, lastname, email, admin == 1);
    }

    public String getHashCode(String username) throws Exception {
        Item item = this.getItemFromUsername(username);
        return (String) item.getItemProperty("password").getValue();
    }

    public boolean userExists(User user) {
        try {
            Item NA = getItemFromUsername(user.getUsername());
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public void createUser(User user, Hash password) throws Exception {

        Object itemId = this.container.addItem();
        Item newUser = this.container.getItem(itemId);
        newUser.getItemProperty(DBConstants.USERS_USERNAME).setValue(
                user.getUsername());
        newUser.getItemProperty(DBConstants.USERS_FIRSTNAME).setValue(
                user.getFirstname());
        newUser.getItemProperty(DBConstants.USERS_LASTNAME).setValue(
                user.getLastname());
        newUser.getItemProperty(DBConstants.USERS_EMAIL).setValue(
                user.getEmail());
        newUser.getItemProperty(DBConstants.USERS_PASSWORD).setValue(
                password.raw());
        newUser.getItemProperty(DBConstants.USERS_ADMIN).setValue(
                new Integer(0));

        try {
            /*
             * First user is admin
             */
            if (isFirst()) {
                newUser.getItemProperty("admin").setValue(new Integer(1));
            }
            this.container.commit();
        } catch (UnsupportedOperationException e) {
            System.err.println("Unsupported");
            e.printStackTrace();
            try {
                this.container.rollback();
                throw new Exception("Failed to create user");
            } catch (SQLException er) {
                er.printStackTrace();
            }
        } catch (SQLException e) {
            System.err.println("SQLError");
            e.printStackTrace();
            try {
                this.container.rollback();
                throw new Exception("Failed to create user");
            } catch (SQLException er) {
                er.printStackTrace();
            }
        }
    }

    /*
     * Check if row count == 0
     */
    private boolean isFirst() throws SQLException {
        return tq.getCount() == 0;
    }

    // public void modifyUser(User user) throws Exception {
    // Item item = this.getItemFromUsername(user.getUsername());
    // item.getItemProperty("username").setValue(user.getUsername());
    // item.getItemProperty("firstname").setValue(user.getUsername());
    // item.getItemProperty("lastname").setValue(user.getUsername());
    // item.getItemProperty("email").setValue(user.getUsername());
    // this.container.commit();
    // }

    public void modifyUser(User user, Hash hash) throws Exception {
        Item item = this.getItemFromUsername(user.getUsername());
        item.getItemProperty(DBConstants.USERS_USERNAME).setValue(
                user.getUsername());
        item.getItemProperty(DBConstants.USERS_FIRSTNAME).setValue(
                user.getFirstname());
        item.getItemProperty(DBConstants.USERS_LASTNAME).setValue(
                user.getLastname());
        item.getItemProperty(DBConstants.USERS_EMAIL).setValue(user.getEmail());
        if (hash != null) {
            item.getItemProperty(DBConstants.USERS_PASSWORD).setValue(
                    hash.raw());
        }
        // item.getItemProperty(DBConstants.USERS_ADMIN).setValue(
        // user.isAdmin() ? 1 : 0);
        this.container.commit();
    }

}
