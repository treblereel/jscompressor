function factorial(n) {
    return n <= 1 ? 1 : n * factorial(n-1);
}
console.log("5! = " + factorial(5));
