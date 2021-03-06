package com.devicehive.dao;

import com.devicehive.auth.HivePrincipal;
import com.devicehive.configuration.Constants;
import com.devicehive.dao.filter.AccessKeyBasedFilterForDevices;
import com.devicehive.model.Device;
import com.devicehive.model.DeviceNotification;
import com.devicehive.model.Network;
import com.devicehive.model.User;
import com.devicehive.util.LogExecutionTime;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotNull;

@Stateless
@LogExecutionTime
public class DeviceNotificationDAO {

    @PersistenceContext(unitName = Constants.PERSISTENCE_UNIT)
    private EntityManager em;


    public DeviceNotification createNotification(DeviceNotification deviceNotification) {
        em.persist(deviceNotification);
        return deviceNotification;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public DeviceNotification findById(@NotNull long id) {
        return em.find(DeviceNotification.class, id);
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<DeviceNotification> findNotifications(Collection<Device> devices, Collection<String> names,
                                                      @NotNull Timestamp timestamp, HivePrincipal principal) {
        if (devices != null && devices.isEmpty()) {
            return Collections.emptyList();
        }
        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<DeviceNotification> criteria = criteriaBuilder.createQuery(DeviceNotification.class);
        Root<DeviceNotification> from = criteria.from(DeviceNotification.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates
            .add(criteriaBuilder.greaterThan(from.<Timestamp>get(DeviceNotification.TIMESTAMP_COLUMN), timestamp));
        if (names != null) {
            predicates.add(from.get(DeviceNotification.NOTIFICATION_COLUMN).in(names));
        }
        if (devices != null) {
            predicates.add(from.get(DeviceNotification.DEVICE_COLUMN).in(devices));
        }
        appendPrincipalPredicates(predicates, principal, from);
        criteria.where(predicates.toArray(new Predicate[predicates.size()]));
        TypedQuery<DeviceNotification> query = em.createQuery(criteria);
        CacheHelper.cacheable(query);
        return query.getResultList();
    }

    /*
     If grid interval is present query must looks like this:

     select * from device_notification
        where device_notification.timestamp in
	 (select min(rank_selection.timestamp)
	 from
		(select device_notification.*,
		       rank() over (partition by device_notification.notification order by floor((extract(EPOCH FROM device_notification.timestamp)) / 30)) as rank
			from device_notification
			where device_notification.timestamp between '2013-04-14 14:23:00.775+04' and '2014-04-14 14:23:00.775+04'
		) as rank_selection
	 where rank_selection.device_id = 8038
       and rank_selection.notification = 'equipment'
	 group by rank_selection.rank, rank_selection.notification);

     If gridInterval is null the query must looks like this:

     select * from device_notification
     where device_notification.timestamp between '2013-04-14 14:23:00.775+04' and '2014-04-14 14:23:00.775+04'
     and  device_id = 8038
     and  notification = 'equipment'

     Order, take and skip parameters will be appended to the end of each query if any of them are present.

     The building of this query contain to stages to avoid sql injection:
     1) Creation query as a string with the wildcards as a parameters. The list of the parameters will be made
     synchronously with the adding of the wildcards.
     2) Query parameters are set with query.setParameter(int position, Object value) to avoid sql injection.
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<DeviceNotification> queryDeviceNotification(Device device,
                                                            Timestamp start,
                                                            Timestamp end,
                                                            String notification,
                                                            String sortField,
                                                            Boolean sortOrderAsc,
                                                            Integer take,
                                                            Integer skip,
                                                            Integer gridInterval) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM device_notification ");     //this part of query is immutable
        if (gridInterval != null) {
            sb.append("WHERE device_notification.timestamp IN ")
                .append("  (SELECT min(rank_selection.timestamp) ")
                .append("  FROM ")
                .append("     (SELECT device_notification.*, ")
                .append("           rank() OVER (PARTITION BY device_notification.notification ORDER BY floor(" +
                        "(extract(EPOCH FROM device_notification.timestamp)) / ?)) AS rank ")
                .append("      FROM device_notification ");
            parameters.add(gridInterval);
        }
        if (start != null && end != null) {
            sb.append(" WHERE device_notification.timestamp BETWEEN ? AND ? ");
            parameters.add(start);
            parameters.add(end);
        } else if (start != null) {
            sb.append(" WHERE device_notification.timestamp >= ? ");
            parameters.add(start);
        } else if (end != null) {
            sb.append(" WHERE device_notification.timestamp <= ? ");
            parameters.add(end);
        }
        if (gridInterval != null) {
            sb.append(" ) AS rank_selection ");
            sb.append("  WHERE (rank_selection.device_id = ?) ");
        } else {
            if (start != null || end != null) {
                sb.append(" AND ");
            } else {
                sb.append(" WHERE ");
            }
            sb.append(" device_notification.device_id = ? ");
        }
        parameters.add(device.getId());   //device id is required
        if (notification != null) {
            sb.append(" AND ");
            if (gridInterval != null) {
                sb.append(" (rank_selection.notification = ?) ");
            } else {
                sb.append(" device_notification.notification = ? ");
            }
            parameters.add(notification);
        }
        if (gridInterval != null) {
            sb.append("  GROUP BY rank_selection.rank, rank_selection.notification) ");   //select min(timestamp),
            // group by is required. Selection should contain first timestamp in the interval. Rank is stands for
            // timestamp in seconds / interval length
        }
        if (sortField != null) {
            sb.append(" ORDER BY ").append(sortField);
            if (sortOrderAsc) {
                sb.append(" ASC ");
            } else {
                sb.append(" DESC ");
            }
        }
        if (take != null) {
            sb.append(" LIMIT ").append(take);
        }
        if (skip != null) {
            sb.append(" OFFSET ").append(skip);
        }
        sb.append(";");
        Query query = em.createNativeQuery(sb.toString(), DeviceNotification.class);
        for (int i = 0; i < parameters.size(); i++) {
            query.setParameter(i + 1, parameters.get(i));
        }
        // Result list will contain only DeviceNotifications according to the query creation
        @SuppressWarnings("unchecked") List<DeviceNotification> result = query.getResultList();
        return result;
    }

    private void appendPrincipalPredicates(List<Predicate> predicates, HivePrincipal principal,
                                           Root<DeviceNotification> from) {
        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        if (principal != null) {
            User user = principal.getUser();
            if (user == null && principal.getKey() != null) {
                user = principal.getKey().getUser();
            }
            if (user != null && !user.isAdmin()) {
                Predicate userPredicate = from.join(DeviceNotification.DEVICE_COLUMN)
                    .join(Device.NETWORK_COLUMN).join(Network.USERS_ASSOCIATION).in(user);
                predicates.add(userPredicate);
            }
            if (principal.getDevice() != null) {
                Predicate devicePredicate = from.get(DeviceNotification.DEVICE_COLUMN).in(principal.getDevice());
                predicates.add(devicePredicate);
            }
            if (principal.getKey() != null) {

                List<Predicate> extraPredicates = new ArrayList<>();
                for (AccessKeyBasedFilterForDevices extraFilter : AccessKeyBasedFilterForDevices
                    .createExtraFilters(principal.getKey().getPermissions())) {
                    List<Predicate> filter = new ArrayList<>();
                    if (extraFilter.getDeviceGuids() != null) {
                        filter.add(
                            from.join(DeviceNotification.DEVICE_COLUMN).get(Device.GUID_COLUMN)
                                .in(extraFilter.getDeviceGuids()
                                ));
                    }
                    if (extraFilter.getNetworkIds() != null) {
                        Predicate networkFilter =
                            from.join(DeviceNotification.DEVICE_COLUMN)
                                .join(Device.NETWORK_COLUMN)
                                .get(Network.ID_COLUMN).in(extraFilter.getNetworkIds());
                        filter.add(networkFilter);
                    }
                    extraPredicates.add(criteriaBuilder.and(filter.toArray(new Predicate[filter.size()])));
                }
                predicates.add(criteriaBuilder.or(extraPredicates.toArray(new Predicate[extraPredicates.size()])));
            }
        }
    }
}
