let names = ["Alice", "Bob", "Carol"];
names.forEach(n => console.log("Hi " + n));
function reverse(s) {
    return s.split("").reverse().join("");
}
console.log(reverse("quarkus"));
