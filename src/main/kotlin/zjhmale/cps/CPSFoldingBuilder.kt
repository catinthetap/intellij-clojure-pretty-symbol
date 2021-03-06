package zjhmale.cps

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import zjhmale.cps.setting.CPSSettings
import java.util.*
import java.util.regex.Pattern

/**
 * Created by zjh on 16/3/22.
 */
class CPSFoldingBuilder : FoldingBuilder {
    private val symbolPattern = Pattern.compile(
            "\\(fn|\\(let|\\(->|\\(def|\\(doseq|partial|comp|not=|and|or|not|>=|<=|#\\(|#\\{|union|difference|intersection"
    )

    private val stringLiteralPattern = Pattern.compile(
            "\".*?\""
    )

    private val prettySymbolMaps = hashMapOf(
            "(fn" to "λ",
            "(let" to "⊢",
            "(letfn" to "λ",
            "(def" to "≡",
            "(defn" to "ƒ",
            "(doseq" to "∀",
            "(->" to "→",
            "(->>" to "⇉",
            "partial" to "Ƥ",
            "comp" to "∘",
            "not=" to "≠",
            "and" to "∧",
            "or" to "∨",
            "not" to "¬",
            ">=" to "≥",
            "<=" to "≤",
            "#(" to "λ(",
            "#{" to "∈{",
            "#{}" to "∅",
            "union" to "⋃",
            "intersection" to "⋂",
            "difference" to "−"
    )
    private val openDelimiters = listOf("(", "{", "[")
    private val closeDelimiters = listOf(")", "}", "]")
    private val setOperators = listOf("union", "difference", "intersection")
    private val leftStopFlags = listOf("(", "[", " ", "/")

    private val GT = { a: Int, b: Int -> a > b }
    private val GE = { a: Int, b: Int -> a >= b }

    private fun isDelimiterMatch(text: String, start: Int, op: (Int, Int) -> Boolean): Boolean {
        var startOffset = start
        var nextChar = ""
        var leftCount = 0
        var rightCount = 0
        while (nextChar != "\n" && startOffset < text.length) {
            nextChar = text.substring(startOffset, startOffset + 1)
            if (openDelimiters.contains(nextChar)) {
                leftCount++
            }
            if (closeDelimiters.contains(nextChar)) {
                rightCount++
            }
            startOffset++
        }
        return op(rightCount, leftCount)
    }

    private fun findLeftStopPos(text: String, start: Int): Int {
        var startOffset = start
        var prevChar = ""
        while (!leftStopFlags.contains(prevChar) && startOffset > 0) {
            prevChar = text.substring(startOffset - 1, startOffset)
            startOffset--
        }
        if (leftStopFlags.contains(prevChar)) {
            return startOffset
        } else {
            return -1
        }
    }

    private val notMacroSymbolPredicate = { text: String, rangeStart: Int, prevChar: String, nextChar: String ->
        ((prevChar == "(" && isDelimiterMatch(text, rangeStart, GT)) || prevChar == " ") && arrayOf(")", " ", "\n").contains(nextChar)
    }

    private val isSymbolInStringLiteral = { text: String, rangeStart: Int, rangeEnd: Int ->
        val matcher = stringLiteralPattern.matcher(text.replace("\n", " "))
        var isInStringLiteral = false
        while (matcher.find()) {
            isInStringLiteral = matcher.start() <= rangeStart && rangeEnd <= matcher.end()
            if (isInStringLiteral) break
        }
        isInStringLiteral
    }

    private fun isSymbolInComment(node: ASTNode, rangeStart: Int, rangeEnd: Int): Boolean {
        var isInComment = false

        if (node.text.startsWith(";")) {
            println("a comment")
            println(node.text)
            println("start ${node.textRange.startOffset}")
            println("end ${node.textRange.endOffset}")
            isInComment = node.textRange.startOffset <= rangeStart && rangeEnd <= node.textRange.endOffset
        }
        val e = node.psi
        if (e.javaClass.toString() == "class cursive.psi.impl.ClSexpComment" && e.text.startsWith("#_")) {
            println("next form")
            println(node.text)
            println("start ${node.textRange.startOffset}")
            println("end ${node.textRange.endOffset}")
            isInComment = node.textRange.startOffset <= rangeStart && rangeEnd <= node.textRange.endOffset
        }

        if (isInComment) return true
        for (child in node.getChildren(null)) {
            if (isSymbolInComment(child, rangeStart, rangeEnd)) return true
        }
        return false
    }

    override fun buildFoldRegions(node: ASTNode, document: Document): Array<out FoldingDescriptor> {
        val settings = CPSSettings.getInstance()
        val descriptors = ArrayList<FoldingDescriptor>()
        val text = node.text
        val matcher = symbolPattern.matcher(text)

        while (matcher.find()) {
            var key = text.substring(matcher.start(), matcher.end())
            val nodeRange = node.textRange
            var rangeStart = nodeRange.startOffset + matcher.start()
            var rangeEnd = nodeRange.startOffset + matcher.end()
            if (key.startsWith("(")) {
                rangeStart += 1
            }

            if (rangeEnd + 1 > text.length || rangeEnd + 2 > text.length) continue
            val nextChar = text.substring(rangeEnd, rangeEnd + 1)
            val nextTwoChars = text.substring(rangeEnd, rangeEnd + 2)
            val prevChar = text.substring(rangeStart - 1, rangeStart)

            val shouldFold =
                    if (key == "(def") {
                        if (nextChar == " ") {
                            settings.turnOnDef && isDelimiterMatch(text, rangeStart, GE)
                        } else if (nextChar == "n") {
                            key = "(defn"
                            rangeEnd += 1
                            settings.turnOnDefn
                        } else {
                            false
                        }

                    } else if (key == "(fn") {
                        settings.turnOnFn && isDelimiterMatch(text, rangeStart, GE)
                    } else if (key == "(->") {
                        if (nextChar == ">") {
                            key = "(->>"
                            rangeEnd += 1
                            settings.turnOnThreadLast && isDelimiterMatch(text, rangeStart, GT)
                        } else if (nextChar == " ") {
                            settings.turnOnThreadFirst && isDelimiterMatch(text, rangeStart, GT)
                        } else {
                            false
                        }
                    } else if (key == "(let") {
                        if (nextTwoChars == "fn") {
                            key = "(letfn"
                            rangeEnd += 2
                            settings.turnOnLetfn && isDelimiterMatch(text, rangeStart, GE)
                        } else if (nextChar == " ") {
                            settings.turnOnLet && isDelimiterMatch(text, rangeStart, GE)
                        } else {
                            false
                        }
                    } else if (key == "(doseq") {
                        nextTwoChars == " [" && settings.turnOnDoseq && isDelimiterMatch(text, rangeStart, GE)
                    } else if (key == "partial") {
                        settings.turnOnPartial && notMacroSymbolPredicate(text, rangeStart, prevChar, nextChar)
                    } else if (key == "comp") {
                        settings.turnOnComp && notMacroSymbolPredicate(text, rangeStart, prevChar, nextChar)
                    } else if (key == "not=") {
                        settings.turnOnNotEqual && notMacroSymbolPredicate(text, rangeStart, prevChar, nextChar)
                    } else if (key == ">=") {
                        settings.turnOnGT && notMacroSymbolPredicate(text, rangeStart, prevChar, nextChar)
                    } else if (key == "<=") {
                        settings.turnOnLT && notMacroSymbolPredicate(text, rangeStart, prevChar, nextChar)
                    } else if (key == "and") {
                        settings.turnOnAnd && notMacroSymbolPredicate(text, rangeStart, prevChar, nextChar)
                    } else if (key == "or") {
                        settings.turnOnOr && notMacroSymbolPredicate(text, rangeStart, prevChar, nextChar)
                    } else if (key == "not") {
                        settings.turnOnNot && notMacroSymbolPredicate(text, rangeStart, prevChar, nextChar)
                    } else if (key == "#(") {
                        settings.turnOnLambda
                    } else if (key == "#{") {
                        if (nextChar == "}") {
                            key = "#{}"
                            rangeEnd += 1
                            settings.turnOnEmptySet
                        } else {
                            settings.turnOnSet
                        }
                    } else if (setOperators.contains(key)) {
                        val leftStopPos = findLeftStopPos(text, rangeStart)
                        if (leftStopPos != -1 && leftStopFlags.contains(prevChar) && isDelimiterMatch(text, rangeStart, GT)) {
                            rangeStart = leftStopPos + 1
                            if (nextChar == " " || nextChar == "]") {
                                if (key == "union") {
                                    settings.turnOnSetUnion
                                } else if (key == "difference") {
                                    settings.turnOnSetDifference
                                } else if (key == "intersection") {
                                    settings.turnOnSetIntersection
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    } else {
                        false
                    }

            if (settings.globalTurnOn
                    && !(isSymbolInComment(node, rangeStart, rangeEnd) && !settings.showUpInComment)
                    && !(isSymbolInStringLiteral(text, rangeStart, rangeEnd) && !settings.showUpInStringLiteral)
                    && shouldFold) {
                val pretty = prettySymbolMaps[key] ?: return arrayOf<FoldingDescriptor>()
                val range = TextRange.create(rangeStart, rangeEnd)
                descriptors.add(CPSFoldingDescriptor(node, range, null, pretty, true))
            }
        }
        return descriptors.toArray<FoldingDescriptor>(arrayOfNulls<FoldingDescriptor>(descriptors.size))
    }

    override fun getPlaceholderText(node: ASTNode) = null

    override fun isCollapsedByDefault(node: ASTNode) = true
}