package li.songe.selector.relation

import li.songe.selector.MatchContext
import li.songe.selector.NodeAdapter
import li.songe.selector.TraversalCandidate
internal class RelationSelector(
    val operator: RelationOperator = RelationOperator.Ancestor,
    val relationExpression: RelationExpression = PolynomialExpression(),
) {
    fun <T : Any> traversal(
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
    ): Sequence<TraversalCandidate<T>> {
        return operator.traversal(context, adapter, relationExpression)
    }

    val isMatchAnyAncestor =
        operator == RelationOperator.Ancestor && relationExpression.matchesAllOffsets

    val isMatchAnyDescendant =
        operator == RelationOperator.Descendant && relationExpression.matchesAllOffsets
}
