#include <stdio.h>
int main() {
  int test = 2;
  int i = 10;
  printf("HELO: %d: %d", test, i);
  printf("JELO\n");
  i = 20;
  test = 4;
  printf("GELO: %d: %d", test, i);
  printf("LELO\n");

  return 0;
}
