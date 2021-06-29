#bad python code because i wanted to test some stuff
import fileinput
import turtle
drawturtle = turtle.Turtle()
drawturtle.penup()
y = 1
for x in fileinput.input():
    print(y,x)
    x = x.split(",")
    a = float(x[0])*20
    b = float(x[1])*20
    drawturtle.goto(a,b)
    drawturtle.pendown()
    y += 1
input()
