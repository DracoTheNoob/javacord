package fr.dtn.javacord.database;

import fr.dtn.javacord.Bot;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.util.*;
import java.util.function.Function;

/**
 * A utility class to manage Hibernate ORM database operations.
 * <p>
 * This class encapsulates Hibernate setup, entity discovery, and CRUD operations
 * with support for criteria-based queries, pagination, sorting, and transactions.
 * <p>
 * It relies on the {@link HibernateEntity} base class for all entities, expects entities
 * to be annotated with {@link jakarta.persistence.Entity} and {@link Table}.
 * <p>
 * Usage example:
 * <pre>
 *     Database db = new Database(bot, dbUrl, dbUser, dbPassword);
 *     Optional<User> user = db.selectById(User.class, userId);
 *     if(user.isPresent()) {
 *         User u = user.get();
 *         // work with u
 *     }
 * </pre>
 * <p>
 * This class is thread-safe for concurrent use.
 * <p>
 * Note: Entity classes are automatically discovered at runtime via reflections scanning.
 */
public class Database {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Scans the classpath and returns all classes extending {@link HibernateEntity} annotated with {@link Entity}.
     *
     * @return array of entity classes to be registered with Hibernate.
     */
    private static Class<?>[] getAllEntities() {
        ConfigurationBuilder config = new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forJavaClassPath())
                .setScanners(Scanners.SubTypes);

        Reflections reflections = new Reflections(config);

        Set<Class<? extends HibernateEntity>> found = reflections.getSubTypesOf(HibernateEntity.class);

        return found.stream()
                .filter(c -> c.isAnnotationPresent(Entity.class))
                .toArray(Class<?>[]::new);
    }

    /**
     * Determines JDBC driver class based on database URL.
     *
     * @param url the JDBC connection URL.
     * @return fully qualified driver class name.
     * @throws IllegalArgumentException if URL does not match supported databases.
     */
    private static String determineDriver(String url) {
        if (url.contains("postgresql")) return "org.postgresql.Driver";
        if (url.contains("mysql")) return "com.mysql.cj.jdbc.Driver";
        if (url.contains("h2")) return "org.h2.Driver";

        throw new IllegalArgumentException("Unknown DB type in URL: " + url);
    }

    /**
     * Determines Hibernate dialect based on database URL.
     *
     * @param url the JDBC connection URL.
     * @return fully qualified Hibernate dialect class name.
     * @throws IllegalArgumentException if URL does not match supported databases.
     */
    private static String determineDialect(String url) {
        if (url.contains("postgresql")) return "org.hibernate.dialect.PostgreSQLDialect";
        if (url.contains("mysql")) return "org.hibernate.dialect.MySQLDialect";
        if (url.contains("h2")) return "org.hibernate.dialect.H2Dialect";

        throw new IllegalArgumentException("Unknown DB dialect for URL: " + url);
    }

    private final Bot bot;
    private final SessionFactory sessionFactory;

    /**
     * Creates a new {@code Database} instance.
     *
     * @param bot      the bot instance, used for debug flag.
     * @param url      JDBC connection URL.
     * @param user     DB username.
     * @param password DB password.
     */
    public Database(Bot bot, String url, String user, String password) {
        this.bot = bot;

        Map<String, Object> settings = new HashMap<>();

        settings.put("hibernate.connection.driver_class", determineDriver(url));
        settings.put("hibernate.connection.url", url);
        settings.put("hibernate.connection.username", user);
        settings.put("hibernate.connection.password", password);
        settings.put("hibernate.dialect", determineDialect(url));
        settings.put("hibernate.hbm2ddl.auto", "update");
        settings.put("hibernate.show_sql", bot.isDebugMode());

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();

        this.sessionFactory = new MetadataSources(registry)
                .addAnnotatedClasses(getAllEntities())
                .buildMetadata()
                .buildSessionFactory();
    }

    /**
     * Selects an entity by its UUID primary key.
     *
     * @param entityClass the entity class type.
     * @param id          the UUID identifier.
     * @param <T>         type of entity.
     * @return Optional containing entity if found, empty otherwise.
     */
    public <T extends HibernateEntity> Optional<T> selectById(Class<T> entityClass, UUID id) {
        try (Session session = sessionFactory.openSession()) {
            T entity = session.find(entityClass, id.toString());
            return Optional.ofNullable(entity);
        }
    }

    /**
     * Inserts or updates an entity in the database.
     *
     * @param entity the entity instance to save or update.
     * @param <T>    type of entity.
     * @throws IllegalArgumentException if entity is null or missing {@code @Table} annotation.
     * @throws RuntimeException         if transaction fails.
     */
    public <T extends HibernateEntity> void insert(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        if (!entity.getClass().isAnnotationPresent(Table.class)) {
            throw new IllegalArgumentException("Entity class must be annotated with @Table");
        }

        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();

            session.merge(entity);

            tx.commit();
            if (bot.isDebugMode()) {
                logger.info("Entity of type {} with id={} saved/updated successfully.", entity.getClass().getSimpleName(), entity.getId());
            }
        } catch (Exception e) {
            if (tx != null) {
                try {
                    tx.rollback();
                    logger.warn("Transaction rolled back due to an error.");
                } catch (Exception rollbackEx) {
                    logger.error("Error during transaction rollback", rollbackEx);
                }
            }
            logger.error("Failed to save or update entity of type {} with id={}", entity.getClass().getSimpleName(), entity.getId(), e);
            throw new RuntimeException("Could not save or update entity", e);
        }
    }

    /**
     * Selects entities matching exact field-value pairs.
     *
     * @param entityClass the entity class type.
     * @param fieldValues map of field names and their expected values.
     * @param <T>         type of entity.
     * @return set of matching entities.
     */
    public <T extends HibernateEntity> Set<T> selectWhere(Class<T> entityClass, Map<String, Object> fieldValues) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<T> query = cb.createQuery(entityClass);
            Root<T> root = query.from(entityClass);

            Predicate predicate = cb.conjunction();

            if (fieldValues != null && !fieldValues.isEmpty()) {
                for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
                    predicate = cb.and(predicate, cb.equal(root.get(entry.getKey()), entry.getValue()));
                }
            }

            query.select(root).where(predicate);

            return new HashSet<>(session.createQuery(query).getResultList());
        }
    }

    /**
     * Selects all entities of a given type.
     *
     * @param entityClass the entity class type.
     * @param <T>         type of entity.
     * @return set of all entities.
     */
    public <T extends HibernateEntity> Set<T> selectAll(Class<T> entityClass) {
        return selectWhere(entityClass, null);
    }

    /**
     * Deletes an entity by its UUID identifier.
     *
     * @param entityClass the entity class type.
     * @param id          the UUID identifier.
     * @param <T>         type of entity.
     * @return true if entity was deleted, false if not found.
     * @throws RuntimeException if transaction fails.
     */
    public <T extends HibernateEntity> boolean deleteById(Class<T> entityClass, UUID id) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            T entity = session.find(entityClass, id.toString());
            if (entity == null) {
                if (bot.isDebugMode()) {
                    logger.info("Entity of type {} with id={} not found for deletion.", entityClass.getSimpleName(), id);
                }
                return false;
            }
            session.remove(entity);
            tx.commit();
            if (bot.isDebugMode()) {
                logger.info("Entity of type {} with id={} deleted successfully.", entityClass.getSimpleName(), id);
            }
            return true;
        } catch (Exception e) {
            if (tx != null) {
                try {
                    tx.rollback();
                    logger.warn("Transaction rolled back due to an error.");
                } catch (Exception rollbackEx) {
                    logger.error("Error during transaction rollback", rollbackEx);
                }
            }
            logger.error("Failed to delete entity of type {} with id={}", entityClass.getSimpleName(), id, e);
            throw new RuntimeException("Could not delete entity", e);
        }
    }

    /**
     * Selects a paginated subset of entities matching the given filters.
     *
     * @param entityClass the entity class type.
     * @param filters     map of field names and expected values.
     * @param offset      zero-based offset of first result.
     * @param limit       maximum number of results to return.
     * @param <T>         type of entity.
     * @return set of entities matching the criteria within the page.
     * @throws IllegalArgumentException if offset < 0 or limit <= 0.
     */
    public <T extends HibernateEntity> Set<T> selectPagedWhere(Class<T> entityClass, Map<String, Object> filters, int offset, int limit) {
        if (offset < 0 || limit <= 0) {
            throw new IllegalArgumentException("Offset must be >= 0 and limit must be > 0");
        }

        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<T> query = cb.createQuery(entityClass);
            Root<T> root = query.from(entityClass);

            Predicate predicate = cb.conjunction();
            if (filters != null && !filters.isEmpty()) {
                for (Map.Entry<String, Object> entry : filters.entrySet()) {
                    predicate = cb.and(predicate, cb.equal(root.get(entry.getKey()), entry.getValue()));
                }
            }
            query.where(predicate);

            var q = session.createQuery(query);
            q.setFirstResult(offset);
            q.setMaxResults(limit);

            return new HashSet<>(q.getResultList());
        }
    }

    /**
     * Selects a paginated subset of all entities.
     *
     * @param entityClass the entity class type.
     * @param offset      zero-based offset of first result.
     * @param limit       maximum number of results to return.
     * @param <T>         type of entity.
     * @return set of entities within the page.
     * @throws IllegalArgumentException if offset < 0 or limit <= 0.
     */
    public <T extends HibernateEntity> Set<T> selectPaged(Class<T> entityClass, int offset, int limit) {
        return selectPagedWhere(entityClass, null, offset, limit);
    }

    /**
     * Selects entities matching filters and sorted by specified fields.
     *
     * @param entityClass the entity class type.
     * @param filters     map of field names and expected values.
     * @param sortFields  map of field names to sort order (true=ascending, false=descending).
     * @param <T>         type of entity.
     * @return set of matching entities sorted accordingly.
     */
    public <T extends HibernateEntity> Set<T> selectWhereSorted(Class<T> entityClass, Map<String, Object> filters, Map<String, Boolean> sortFields) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<T> query = cb.createQuery(entityClass);
            Root<T> root = query.from(entityClass);

            Predicate predicate = cb.conjunction();
            if (filters != null && !filters.isEmpty()) {
                for (Map.Entry<String, Object> entry : filters.entrySet()) {
                    predicate = cb.and(predicate, cb.equal(root.get(entry.getKey()), entry.getValue()));
                }
            }
            query.where(predicate);

            if (sortFields != null && !sortFields.isEmpty()) {
                List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
                for (Map.Entry<String, Boolean> entry : sortFields.entrySet()) {
                    if (entry.getValue()) {
                        orders.add(cb.asc(root.get(entry.getKey())));
                    } else {
                        orders.add(cb.desc(root.get(entry.getKey())));
                    }
                }
                query.orderBy(orders);
            }

            return new HashSet<>(session.createQuery(query).getResultList());
        }
    }

    /**
     * Counts total number of entities of a given type.
     *
     * @param entityClass the entity class type.
     * @param <T>         type of entity.
     * @return total count of entities.
     */
    public <T extends HibernateEntity> long count(Class<T> entityClass) {
        try (Session session = sessionFactory.openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<T> root = query.from(entityClass);
            query.select(cb.count(root));

            return session.createQuery(query).getSingleResult();
        }
    }

    /**
     * Checks if an entity exists by UUID identifier.
     *
     * @param entityClass the entity class type.
     * @param id          the UUID identifier.
     * @param <T>         type of entity.
     * @return true if entity exists, false otherwise.
     */
    public <T extends HibernateEntity> boolean existsById(Class<T> entityClass, UUID id) {
        try (Session session = sessionFactory.openSession()) {
            T entity = session.find(entityClass, id.toString());
            return entity != null;
        }
    }

    /**
     * Executes arbitrary work inside a transaction and returns a result.
     *
     * @param work a function receiving a {@link Session} and returning a result.
     * @param <R>  the result type.
     * @return the result from the transactional work.
     * @throws RuntimeException if transaction fails.
     */
    public <R> R doInTransaction(Function<Session, R> work) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            R result = work.apply(session);
            tx.commit();
            return result;
        } catch (Exception e) {
            if (tx != null) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    logger.error("Error during transaction rollback", rollbackEx);
                }
            }
            logger.error("Transaction failed", e);
            throw new RuntimeException("Transaction failed", e);
        }
    }
}
