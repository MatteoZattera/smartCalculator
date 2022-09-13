import java.math.BigInteger

class InvalidExpressionException(message: String = "Invalid expression") : Exception(message)
class InvalidIdentifierException(message: String = "Invalid identifier") : Exception(message)
class InvalidAssignmentException(message: String = "Invalid assignment") : Exception(message)
class UnknownVariableException(message: String = "Unknown variable") : Exception(message)

fun main() {
    Calculator.run()
}

/**
 * @author Matteo Zattera
 * @since 13/09/2022
 */
object Calculator {
    private const val HELP_COMMAND = "/help"
    private const val EXIT_COMMAND = "/exit"
    private const val INFO = "The program can solve and save expressions with integers numbers and + - * / ^ operators.\n" +
            "  Saving an expression   ->    identifier = expression [ example: x = (5 * (-2 + -6))  ]\n" +
            "  Saving a value         ->    identifier = value      [ example: y = 4   or   y = x   ]\n" +
            "  Evaluate an expression ->    expression              [ example: 6 ^ 2 * -(-(20 / y)) ]\n" +
            "Spaces are ignored, write /help for info, /exit for terminate the program."
    private val variables = mutableMapOf<String, BigInteger>()

    /** Runs the calculator */
    fun run() {
        while (true) {
            val input = readln()
            try {
                when {
                    input.isBlank()       -> continue
                    input == EXIT_COMMAND -> break
                    input == HELP_COMMAND -> println(INFO)
                    input.first() == '/'  -> println("Unknown command")
                    input.contains('=')   -> saveValue(input)
                    else                  -> println(evaluate(input))
                }
            } catch (e: Exception) {
                println(e.message)
            }
        }
        println("Bye!")
    }

    /**
     * Saves the result of an expression in a variable, the expression can be a single value
     * @param str the string representing the couple identifier expression
     * @throws UnknownVariableException if one of the variables in the expression do not exist
     * @throws InvalidIdentifierException if the identifier is invalid
     * @throws InvalidAssignmentException if the expression is invalid
     */
    private fun saveValue(str: String) {
        val parts = str.replace("\\s+".toRegex(), " ")
            .replace("\\s*=\\s*".toRegex(), "=")
            .trim()
            .split("=".toRegex(), 2)
        val identifier = parts[0]
        val value = parts[1]

        if (!identifier.matches("[a-zA-Z]+".toRegex())) throw InvalidIdentifierException()

        try {
            variables[identifier] = evaluate(value)
        } catch (e: UnknownVariableException) {
            throw UnknownVariableException()
        } catch (e: Exception) {
            throw InvalidAssignmentException()
        }
    }

    /**
     * Returns the result of the expression [str]
     * @param str the string representing the expression to evaluate
     * @throws InvalidExpressionException if one operator is not valid
     * @throws NumberFormatException if at least one operand is invalid
     * @throws UnknownVariableException if one of the variables do not exist
     * @throws InvalidIdentifierException if an identifier is invalid
     * @throws ArithmeticException if secondOperand is zero or BigInteger overflow
     */
    private fun evaluate(str: String): BigInteger {

        var s = str
        if (s.matches("[\\s\\da-zA-Z()^*/+-]+".toRegex())          // If s contains only spaces, parenthesis, numbers, latin letters and/or operators
            && !s.contains("[a-zA-Z0-9]\\s+[a-zA-Z0-9]".toRegex()) // If s do not contain letters or numbers separated by spaces
            && validParenthesis(s)                                 // If parenthesis are valid
            && s.isNotBlank()                                      // If s is not "" or " " or "  " or "   " etc.
        ) {
            if (s.contains("\\d[a-zA-Z]|[a-zA-Z]\\d".toRegex())) throw InvalidIdentifierException()

            s = removeConsecutivePlusAndMinusOperators(s.replace(" ", ""))
                .replace("(?<=[a-zA-Z0-9)])([*/^+-])(?=[a-zA-Z0-9(])".toRegex(), " $1 ")
                .replace("([*/^])(?=[+-])".toRegex(), " $1 ")
            s = solve(s)

        } else throw InvalidExpressionException()

        return s.toBigIntegerOrNull() ?: throw InvalidExpressionException()
    }

    /**
     * Returns the result of the expression without parenthesis [str]
     * @param str the string representing the expression (without parenthesis) to evaluate
     * @throws InvalidExpressionException if one operator is not valid
     * @throws NumberFormatException if at least one operand is invalid
     * @throws UnknownVariableException if one of the variables do not exist
     * @throws ArithmeticException if secondOperand is zero or BigInteger overflow
     */
    private fun solve(str: String): String {

        var e = parseOperand(str) // if str is not a single operand then e will be equal to str

        while (e.contains("\\([^()]+\\)".toRegex())) {
            val expressionBetweenParenthesis = e.replaceFirst(".*?\\(([^()]+)\\).*".toRegex(), "$1")
            e = e.replaceFirst("\\([^()]+\\)".toRegex(), solve(expressionBetweenParenthesis))
        }

        while (e.contains("[+-]?[a-zA-Z0-9]+ \\^ [+-]?[a-zA-Z0-9]+".toRegex())) { // ^
            val operation = e.replaceFirst(".*?([+-]?[a-zA-Z0-9]+ \\^ [+-]?[a-zA-Z0-9]+).*".toRegex(), "$1")
            e = e.replaceFirst(operation, calculate(operation))
        }
        while (e.contains("[+-]?[a-zA-Z0-9]+ [*/] [+-]?[a-zA-Z0-9]+".toRegex())) { // * /
            val operation = e.replaceFirst(".*?([+-]?[a-zA-Z0-9]+ [*/] [+-]?[a-zA-Z0-9]+).*".toRegex(), "$1")
            e = e.replaceFirst(operation, calculate(operation))
        }
        while (e.contains("[+-]?[a-zA-Z0-9]+ [+-] [+-]?[a-zA-Z0-9]+".toRegex())) { // + -
            val operation = e.replaceFirst(".*?([+-]?[a-zA-Z0-9]+ [+-] [+-]?[a-zA-Z0-9]+).*".toRegex(), "$1")
            e = e.replaceFirst(operation, calculate(operation))
        }
        return e
    }

    /**
     * Returns the result of the operation by two operands. The string must be an operand followed by a space,
     * followed by the operator, followed by a space and followed by the other operand
     * @param str the string containing two operands and the operator
     * @throws InvalidExpressionException if the operator is not valid
     * @throws NumberFormatException if at least one operand is invalid
     * @throws UnknownVariableException if one of the variables do not exist
     * @throws ArithmeticException if secondOperand is zero or BigInteger overflow
     */
    private fun calculate(str: String): String {

        val parts = str.split(" ", limit = 3)
        val firstOperand = parseOperand(parts[0]).toBigInteger()
        val secondOperand = parseOperand(parts[2]).toBigInteger()

        return when (parts[1]) {
            "^"  -> firstOperand.pow(secondOperand.toInt())
            "*"  -> firstOperand * secondOperand
            "/"  -> firstOperand / secondOperand
            "+"  -> firstOperand + secondOperand
            "-"  -> firstOperand - secondOperand
            else -> throw InvalidExpressionException()
        }.toString()
    }

    /**
     * Returns true if the expression [str] has valid parenthesis or has no parenthesis, otherwise false
     * @param str the string containing parenthesis
     */
    private fun validParenthesis(str: String): Boolean {
        var flag = 0
        for (ch in str) {
            if (ch == '(') flag++
            else if (ch == ')') flag--
            if (flag < 0) return false
        }
        return flag == 0
    }

    /**
     * Returns [str] with consecutive plus and/or minus operators replaced by the resulting operator
     * @param str the string that contains consecutive plus and/or minus operators
     */
    private fun removeConsecutivePlusAndMinusOperators(str: String): String {
        var result = str
        while (result.contains("\\+\\+|--|-\\+|\\+-".toRegex())) {
            result = result.replace("++", "+").replace("--", "+")
                .replace("-+", "-").replace("+-", "-")
        }
        return result
    }

    /**
     * If argument is a variable returns its value, otherwise returns the parameter [operand]
     * @param operand the string to parse
     * @throws UnknownVariableException if the variables do not exist
     */
    private fun parseOperand(operand: String): String {
        if (operand.matches("[+-]?[a-zA-Z]+".toRegex())) {
            val sign = if (operand.startsWith('-')) -1 else 1
            val identifier = operand.replace(".*?([a-zA-Z]+)".toRegex(), "$1")

            if (variables.containsKey(identifier)) {
                return (variables[identifier]!! * sign.toBigInteger()).toString()
            } else throw UnknownVariableException()
        }
        return operand
    }
}
