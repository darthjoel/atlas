package com.ning.atlas;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.ning.atlas.spi.Identity;
import com.ning.atlas.spi.My;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SystemTemplate extends Template
{
    private final List<Template> children = Lists.newArrayList();

    @Deprecated
    public SystemTemplate(String name)
    {
        this(name, Collections.<String, Object>emptyMap());
    }

    public SystemTemplate(String name, Map<String, Object> my)
    {
        super(name, new My(my));
    }

    public void addChildren(Iterable<? extends Template> normalize)
    {
        Iterables.addAll(children, normalize);
    }

    public void addChild(Template child)
    {
        children.add(child);
    }

    public Collection<? extends Template> getChildren()
    {
        return children;
    }

    public List<NormalizedTemplate> _nom(Identity parent)
    {
        List<NormalizedTemplate> rs = Lists.newArrayList();
        List<String> node_names = getCardinality();
        for (String node_name : node_names) {

            Identity id = parent.createChild(getType(), node_name);

            List<NormalizedTemplate> chillins = Lists.newArrayListWithCapacity(getChildren().size());

            for (Template child : getChildren()) {
                Iterable<NormalizedTemplate> r2 = child._nom(id);
                Iterables.addAll(chillins, r2);
            }
            NormalizedSystemTemplate me = new NormalizedSystemTemplate(id, getMy(), chillins);
            rs.add(me);
        }
        return rs;
    }
}
