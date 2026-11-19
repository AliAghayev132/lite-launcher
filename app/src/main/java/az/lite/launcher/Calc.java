package az.lite.launcher;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Axtarış üçün kiçik kalkulyator: + - * / ^ %, mötərizə, ×, ÷, vergüllü ədədlər.
 * İfadə deyilsə null qaytarır (adi axtarış kimi davam edir).
 */
final class Calc {

    private final String s;
    private int pos;

    private Calc(String s) { this.s = s; }

    static String eval(String input) {
        if (input == null) return null;
        String t = input.replace(" ", "").replace('×', '*').replace('x', '*').replace('X', '*')
                .replace('÷', '/').replace(':', '/').replace(',', '.').replace('−', '-');
        if (t.isEmpty() || t.length() > 64) return null;
        boolean digit = false, op = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') digit = true;
            else if ("+-*/^%".indexOf(c) >= 0) { if (i > 0) op = true; }
            else if (c != '.' && c != '(' && c != ')') return null;
        }
        if (!digit || !op) return null;
        try {
            Calc p = new Calc(t);
            double v = p.expr();
            if (p.pos != t.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return format(v);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        BigDecimal bd = new BigDecimal(v).round(new MathContext(12)).stripTrailingZeros();
        String r = Math.abs(v) >= 1e15 || Math.abs(v) < 1e-9 ? bd.toString() : bd.toPlainString();
        return r.replace('.', ',');
    }

    private char peek() { return pos < s.length() ? s.charAt(pos) : 0; }

    private double expr() {
        double v = term();
        while (true) {
            char c = peek();
            if (c == '+') { pos++; v += term(); }
            else if (c == '-') { pos++; v -= term(); }
            else return v;
        }
    }

    private double term() {
        double v = power();
        while (true) {
            char c = peek();
            if (c == '*') { pos++; v *= power(); }
            else if (c == '/') { pos++; double d = power(); if (d == 0) throw new ArithmeticException(); v /= d; }
            else if (c == '(') { v *= power(); } // 2(3+1)
            else return v;
        }
    }

    private double power() {
        double b = unary();
        if (peek() == '^') { pos++; return Math.pow(b, power()); }
        return b;
    }

    private double unary() {
        char c = peek();
        if (c == '-') { pos++; return -unary(); }
        if (c == '+') { pos++; return unary(); }
        return postfix();
    }

    private double postfix() {
        double v = atom();
        while (peek() == '%') { pos++; v /= 100.0; }
        return v;
    }

    private double atom() {
        char c = peek();
        if (c == '(') {
            pos++;
            double v = expr();
            if (peek() != ')') throw new IllegalStateException();
            pos++;
            return v;
        }
        int start = pos;
        while (pos < s.length() && ((s.charAt(pos) >= '0' && s.charAt(pos) <= '9') || s.charAt(pos) == '.')) pos++;
        if (start == pos) throw new IllegalStateException();
        return Double.parseDouble(s.substring(start, pos));
    }
}
