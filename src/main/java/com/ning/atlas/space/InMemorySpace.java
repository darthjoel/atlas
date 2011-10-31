package com.ning.atlas.space;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Maps;
import com.ning.atlas.base.Maybe;
import com.ning.atlas.spi.Identity;
import com.ning.atlas.spi.Space;
import com.ning.atlas.spi.SpaceKey;
import org.apache.commons.beanutils.PropertyUtils;
import org.codehaus.jackson.map.ObjectMapper;

import java.beans.PropertyDescriptor;
import java.util.Map;

public class InMemorySpace implements Space
{
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<SpaceKey, String> values = Maps.newConcurrentMap();

    public static Space newInstance()
    {
        return new InMemorySpace();
    }

    @Override
    public void store(Identity id, Object it)
    {
        PropertyDescriptor[] pds = PropertyUtils.getPropertyDescriptors(it.getClass());
        for (PropertyDescriptor pd : pds) {
            if (!pd.getReadMethod().getDeclaringClass().equals(Object.class)) {
                String prop_name = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, pd.getName());
                try {
                    Object value = pd.getReadMethod().invoke(it);
                    String json = String.valueOf(value);
                    values.put(SpaceKey.from(id, prop_name), json);
                }
                catch (Exception e) {
                    throw new IllegalStateException("unable to read property '" + pd.getName() + "' from " + it, e);
                }
            }
        }
    }

    @Override
    public void store(Identity id, String key, String value)
    {
        this.values.put(SpaceKey.from(id, key), value);
    }

    @Override
    public void scratch(String key, String value)
    {
        this.values.put(SpaceKey.from(Identity.root(), key), value);
    }

    @Override
    public Maybe<String> get(String key)
    {
        return Maybe.elideNull(this.values.get(SpaceKey.from(Identity.root(), key)));
    }

    @Override
    public Maybe<String> get(Identity id, String key)
    {
        return Maybe.elideNull(values.get(SpaceKey.from(id, key)));
    }

    @Override
    public <T> Maybe<T> get(Identity id, Class<T> type, Missing behavior)
    {
        PropertyDescriptor[] pds = PropertyUtils.getPropertyDescriptors(type);
        T bean;
        try {
            bean = type.newInstance();
        }
        catch (Exception e) {
            throw new IllegalStateException("unable to instantiate an instance of " + type.getName(), e);
        }

        for (PropertyDescriptor pd : pds) {
            if (pd.getWriteMethod() != null) {
                String key = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, pd.getName());
                String json_val = this.values.get(SpaceKey.from(id, key));
                final Object val;
                if (json_val != null) {
                    val =  mapper.convertValue(String.valueOf(json_val), pd.getPropertyType());
                }
                else {
                    switch (behavior) {
                        case NullProperty:
                            val = null;
                            break;
                        case RequireAll:
                            return Maybe.unknown();
                        default:
                            throw new UnsupportedOperationException("Not Yet Implemented!");
                    }
                }

                try {
                    pd.getWriteMethod().invoke(bean, val);
                }
                catch (Exception e) {
                    throw new IllegalStateException("unable to write property " + pd.getName() + " to " + bean, e);
                }
            }
        }

        return Maybe.definitely(bean);
    }

    @Override
    public String require(String s)
    {
        Maybe<String> m = get(s);
        if (m.isKnown()) {
            return m.getValue();
        }
        else {
            throw new IllegalStateException("required value for " + s + " has not been defined");
        }
    }

    @Override
    public Map<SpaceKey, String> getAllFor(Identity id)
    {
        Map<SpaceKey, String> rs = Maps.newHashMap();
        for (Map.Entry<SpaceKey, String> entry : values.entrySet()) {
            if (entry.getKey().getIdentity().equals(id)) {
                rs.put(entry.getKey(), entry.getValue());
            }
        }
        return rs;
    }
}
