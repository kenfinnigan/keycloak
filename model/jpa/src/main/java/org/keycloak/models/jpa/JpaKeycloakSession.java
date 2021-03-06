package org.keycloak.models.jpa;

import org.keycloak.models.*;
import org.keycloak.models.jpa.entities.*;
import org.keycloak.models.utils.KeycloakSessionUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class JpaKeycloakSession implements KeycloakSession {
    protected EntityManager em;

    public JpaKeycloakSession(EntityManager em) {
        this.em = em;
    }

    @Override
    public KeycloakTransaction getTransaction() {
        return new JpaKeycloakTransaction(em);
    }

    @Override
    public RealmModel createRealm(String name) {
        return createRealm(KeycloakSessionUtils.generateId(), name);
    }

    @Override
    public RealmModel createRealm(String id, String name) {
        RealmEntity realm = new RealmEntity();
        realm.setName(name);
        realm.setId(id);
        em.persist(realm);
        em.flush();
        return new RealmAdapter(em, realm);
    }

    @Override
    public RealmModel getRealm(String id) {
        RealmEntity realm = em.find(RealmEntity.class, id);
        if (realm == null) return null;
        return new RealmAdapter(em, realm);
    }

    @Override
    public List<RealmModel> getRealms(UserModel admin) {
        TypedQuery<RealmEntity> query = em.createQuery("select r from RealmEntity r", RealmEntity.class);
        List<RealmEntity> entities = query.getResultList();
        List<RealmModel> realms = new ArrayList<RealmModel>();
        for (RealmEntity entity : entities) {
            realms.add(new RealmAdapter(em, entity));
        }
        return realms;
    }

    @Override
    public boolean removeRealm(String id) {
        RealmEntity realm = em.find(RealmEntity.class, id);
        if (realm == null) {
            return false;
        }

        RealmAdapter adapter = new RealmAdapter(em, realm);
        for (ApplicationEntity a : new LinkedList<ApplicationEntity>(realm.getApplications())) {
            adapter.removeApplication(a.getId());
        }

        for (UserEntity u : em.createQuery("from UserEntity", UserEntity.class).getResultList()) {
            adapter.removeUser(u.getLoginName());
        }

        em.createQuery("delete from " + OAuthClientEntity.class.getSimpleName() + " where realm = :realm").setParameter("realm", realm).executeUpdate();

        em.remove(realm);

        return true;
    }

    @Override
    public void close() {
        if (em.getTransaction().isActive()) em.getTransaction().rollback();
        if (em.isOpen()) em.close();
    }
}
