package com.ning.atlas.tree;

import com.google.common.collect.Lists;

import java.util.List;

public class Trees
{
    public static <TreeType extends Tree<TreeType>, BatonType> BatonType visit(TreeType tree,
                                                                               BatonType baton,
                                                                               Visitor<TreeType, BatonType> visitor)
    {
        return new Director<TreeType, BatonType>(tree, visitor).apply(baton);
    }

    public static <TreeType extends Tree<TreeType>> List<TreeType> leaves(TreeType root)
    {
        return visit(root, Lists.<TreeType>newArrayList(), new BaseVisitor<TreeType, List<TreeType>>() {

            public List<TreeType> on(TreeType node, List<TreeType> baton)
            {
                if (!node.getChildren().iterator().hasNext()) {
                    baton.add(node);
                }
                return baton;
            }
        });
    }

    private static class Director<TreeType extends Tree<TreeType>, BatonType>
    {
        private final TreeType tree;
        private final Visitor<TreeType, BatonType> visitor;

        Director(TreeType tree, Visitor<TreeType, BatonType> visitor)
        {
            this.tree = tree;
            this.visitor = visitor;
        }

        public BatonType apply(BatonType input)
        {
            BatonType b = visitor.enter(tree, input);
            b = visitor.on(tree, b);
            for (TreeType child : (Iterable<TreeType>) tree.getChildren()) {
                Director<TreeType, BatonType> d = new Director<TreeType, BatonType>(child, visitor);
                b = d.apply(b);
            }
            return visitor.exit(tree, b);
        }
    }
}